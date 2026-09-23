// 手机回收查价 - C++ 轻量版（Win32 原生，单 exe < 1MB）
// 数据源：本地 API 服务 http://127.0.0.1:8760（未运行时自动拉起 Node 服务）
// 表格：机型 | 规格 | 回收价(高亮) | 更新(当日整行标记)，按系列分组陈列
#include <windows.h>
#include <windowsx.h>
#include <commctrl.h>
#include <string>
#include <vector>
#include <algorithm>
#include <thread>
#include <atomic>

#include "json.h"
#include "http.h"

#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "user32.lib")

// ---------- 常量 ----------
#define PORT 8760
const wchar_t* HOST = L"127.0.0.1";

#define WM_APP_SERVER_OK   (WM_APP + 1)
#define WM_APP_SERVER_FAIL (WM_APP + 2)
#define WM_APP_DATA_READY  (WM_APP + 3)

const COLORREF CLR_BG        = RGB(245, 246, 248);
const COLORREF CLR_BORDER    = RGB(221, 226, 233);
const COLORREF CLR_PRICE     = RGB(13, 60, 160);
const COLORREF CLR_TEXT      = RGB(32, 40, 52);
const COLORREF CLR_SUB       = RGB(120, 130, 144);
const COLORREF CLR_TODAY_BG  = RGB(230, 240, 255);
const COLORREF CLR_TODAY_BAR = RGB(22, 119, 255);
const COLORREF CLR_ACCENT    = RGB(22, 119, 255);

// ---------- 数据模型 ----------
struct Variant { std::string spec; std::string price; };
struct Model {
    long long id = 0;
    std::string brand, category, model, price, note, updated_at;
    std::string series;              // 分组键（品牌 + 分类）
    std::vector<Variant> variants;
};

// ---------- 全局状态 ----------
HWND g_hWnd = nullptr;
HWND g_hSearch = nullptr;
HWND g_hList = nullptr;
HWND g_hStatus = nullptr;
HWND g_hRefresh = nullptr;
HWND g_btnPrev = nullptr;
HWND g_btnNext = nullptr;
std::vector<Model> g_models;
std::vector<size_t> g_view;
std::vector<std::string> g_brands;
std::vector<int> g_brandX;          // 品牌标签起始 x（未滚动坐标，从 16 起）
std::string g_curBrand = "全部";
std::string g_curSearch;
std::string g_lastUpdated;
std::atomic<bool> g_loading{false};
int g_sortCol = 0;
bool g_sortDesc = false;
int g_scrollX = 0;                  // 品牌条滚动偏移
HFONT g_fontUI = nullptr;
HFONT g_fontBig = nullptr;
HFONT g_fontSmall = nullptr;

const int TB_H = 48;
const int BRAND_H = 40;
const int STATUS_H = 26;
const int BRAND_CTRL_W = 104;       // 品牌条右侧箭头按钮区宽度

// ---------- 工具 ----------
static std::string trim(const std::string& s) {
    size_t a = s.find_first_not_of(" \t\r\n"), b = s.find_last_not_of(" \t\r\n");
    return (a == std::string::npos) ? "" : s.substr(a, b - a + 1);
}
static std::string todayStr() {
    SYSTEMTIME st; GetLocalTime(&st);
    char buf[16]; snprintf(buf, sizeof(buf), "%04d-%02d-%02d", st.wYear, st.wMonth, st.wDay);
    return buf;
}
static bool isToday(const std::string& ts) { return ts.size() >= 10 && ts.substr(0, 10) == todayStr(); }
static std::string specText(const Model& m) {
    std::string out;
    for (auto& v : m.variants) {
        if (!v.spec.empty()) { if (!out.empty()) out += " / "; out += v.spec; }
    }
    return out;
}
static std::wstring wstr(const std::string& s) { return http::utf8ToWide(s); }
static std::string shortTime(const std::string& ts) { return ts.size() >= 16 ? ts.substr(5, 11) : ts; }


// 品牌优先级（Apple 第一，其次 vivo/OPPO/华为/荣耀/小米/一加/魅族，其余按拼音）
static int brandRank(const std::string& b) {
    static const char* prio[] = { "Apple", "vivo", "OPPO", "华为", "荣耀", "小米", "一加", "魅族" };
    for (int i = 0; i < 8; i++) if (b == prio[i]) return i;
    return 100;
}
static bool brandLess(const std::string& a, const std::string& b) {
    int ra = brandRank(a), rb = brandRank(b);
    if (ra != rb) return ra < rb;
    return wstr(a).compare(wstr(b)) < 0;   // 中文按拼音
}


// ---------- 系列提取（按品牌官方产品系列，与手机端 App 首页一致） ----------
static std::string normModel(const std::string& s) {
    std::string r;
    for (char c : s) {
        if (c == ' ' || c == '\t' || c == '\r' || c == '\n') continue;
        r += (c >= 'A' && c <= 'Z') ? (char)(c - 'A' + 'a') : c;
    }
    return r;
}
static bool startsWith(const std::string& s, const std::string& p) {
    return s.size() >= p.size() && s.compare(0, p.size(), p) == 0;
}
static bool startsDigit(const std::string& s) { return !s.empty() && s[0] >= '0' && s[0] <= '9'; }

// 各品牌：型号前缀 -> 官方系列名（长前缀优先）
static std::string oppoSeries(const std::string& n) {
    if (startsWith(n, "findn")) return "Find N系列";      // 折叠屏
    if (startsWith(n, "find")) return "Find X系列";       // find x7/x8/x9 与老 Findx2~X6
    if (startsWith(n, "reno")) return "Reno系列";
    if (startsWith(n, "oppopad")) return "平板系列";
    if (startsWith(n, "k")) return "K系列";
    if (startsWith(n, "a")) return "A系列";
    if (startsWith(n, "r")) return "R系列";
    if (startsWith(n, "n")) return "N系列";               // N1/N2/N3 旋转摄像头
    if (startsWith(n, "x")) return "X系列";               // X9007/X909
    return "其他";
}
static std::string vivoSeries(const std::string& n) {
    if (startsWith(n, "iqoo")) return "iQOO系列";
    if (startsWith(n, "nex")) return "NEX系列";
    if (startsWith(n, "xplay")) return "Xplay系列";
    if (startsWith(n, "vivopad")) return "平板系列";
    if (startsWith(n, "x")) return "X系列";               // X5~X300、X Fold/X Flip/X Note
    if (startsWith(n, "s")) return "S系列";
    if (startsWith(n, "y")) return "Y系列";
    if (startsWith(n, "z")) return "Z系列";
    if (startsWith(n, "t")) return "T系列";
    if (startsWith(n, "v")) return "V系列";
    if (startsWith(n, "u")) return "U系列";
    return "其他";
}
static std::string huaweiSeries(const std::string& n) {
    std::string m = startsWith(n, "华为") ? n.substr(2) : n;
    if (m.find("平板") != std::string::npos || m.find("pad") != std::string::npos) return "平板系列";
    if (startsWith(m, "mate")) return "Mate系列";
    if (startsWith(m, "pocket")) return "Pocket系列";
    if (startsWith(m, "pura")) return "Pura系列";
    if (startsWith(m, "hinova")) return "nova系列";
    if (startsWith(m, "nova")) return "nova系列";          // 含 nova flip
    if (startsWith(m, "畅玩")) return "畅玩系列";
    if (startsWith(m, "hichangxiang")) return "畅享系列";  // Hi畅享
    if (startsWith(m, "优畅享")) return "畅享系列";
    if (startsWith(m, "畅享")) return "畅享系列";
    if (startsWith(m, "麦芒")) return "麦芒系列";
    if (startsWith(m, "nzone")) return "nzone系列";
    if (startsWith(m, "荣耀")) return "荣耀系列";
    if (startsWith(m, "g")) return "G系列";                // G7/G9
    if (startsWith(m, "p")) return "Pura系列";             // P6~P70（原P系列）
    if (startsWith(m, "c") || startsWith(m, "e")) return "畅享系列";  // C/E 老款
    if (startsDigit(m)) return "畅享系列";             // 4x/5c/5x/6a/9plus
    return "其他";
}
static std::string honorSeries(const std::string& n) {
    std::string m = startsWith(n, "荣耀") ? n.substr(2) : n;
    if (m.find("平板") != std::string::npos) return "平板系列";
    if (startsWith(m, "magic") || startsWith(m, "魔术")) return "Magic系列";
    if (startsWith(m, "play")) return "Play系列";
    if (startsWith(m, "note")) return "Note系列";
    if (startsWith(m, "畅玩")) return "畅玩系列";
    if (startsWith(m, "v")) return "V系列";                // V8~V40
    if (startsWith(m, "x")) return "X系列";
    if (startsWith(m, "gt")) return "GT系列";
    if (startsDigit(m)) {
        if (m.size() > 1 && m[1] == 'x') return "X系列";   // 8X/9X
        if (m.size() > 1 && m[1] == 'a') return "A系列";   // 4A/5A/6A
        if (m.size() > 1 && m[1] == 'c') return "畅玩系列"; // 4C/6C/7C
        return "数字系列";                                  // 10/20/30/50/60/70/80/90/100/200/300/400/500
    }
    return "其他";
}
static std::string xiaomiSeries(const std::string& n) {
    if (startsWith(n, "redmi")) return "Redmi系列";
    if (startsWith(n, "mix") || startsWith(n, "mlx")) return "MIX系列";  // mlx fold 为 mix fold 误写
    if (startsWith(n, "小米")) {
        std::string m = n.substr(2);
        if (m.find("平板") != std::string::npos) return "平板系列";
        if (startsWith(m, "mix")) return "MIX系列";
        if (startsWith(m, "civi")) return "Civi系列";
        if (startsWith(m, "cc")) return "CC系列";
        if (startsWith(m, "note")) return "Note系列";
        if (startsWith(m, "max")) return "Max系列";
        if (startsWith(m, "play")) return "Play系列";
        if (startsWith(m, "黑鲨")) return "黑鲨系列";
        if (startsWith(m, "poco")) return "POCO系列";
        if (startsDigit(m)) return "数字系列";          // 10/11/12/13/14/15/17...
        return "其他";
    }
    return "其他";
}
static std::string oneplusSeries(const std::string& n) {
    if (startsWith(n, "一加")) {
        std::string m = n.substr(2);
        if (startsWith(m, "ace")) return "Ace系列";
        if (startsWith(m, "turbo")) return "Turbo系列";
        if (startsDigit(m)) return "数字系列";
        return "其他";
    }
    if (startsWith(n, "1+nord")) return "Nord系列";
    if (startsWith(n, "1+")) return "数字系列";            // 1+3~1+13
    if (startsWith(n, "zuk")) return "ZUK系列";
    return "其他";
}
static std::string meizuSeries(const std::string& n) {
    if (startsWith(n, "魅蓝")) return "魅蓝系列";
    if (startsWith(n, "魅族")) {
        std::string m = n.substr(2);
        if (startsWith(m, "lucky")) return "Lucky系列";
        if (startsWith(m, "mx")) return "MX系列";
        if (startsWith(m, "pro")) return "PRO系列";
        if (startsWith(m, "v")) return "V系列";
        if (startsWith(m, "x")) return "X系列";
        if (startsDigit(m)) return "数字系列";          // 15plus~21pro
        return "其他";
    }
    return "其他";
}
// 主入口：返回该机型所属系列名；非主流品牌返回空串（回退用 category 分组）
static std::string seriesOf(const std::string& brand, const std::string& model) {
    std::string n = normModel(model);
    if (brand == "OPPO") return oppoSeries(n);
    if (brand == "vivo") return vivoSeries(n);
    if (brand == "华为") return huaweiSeries(n);
    if (brand == "荣耀") return honorSeries(n);
    if (brand == "小米") return xiaomiSeries(n);
    if (brand == "一加") return oneplusSeries(n);
    if (brand == "魅族") return meizuSeries(n);
    return "";
}
// ---------- HTTP ----------
static bool httpGetText(const std::string& path, std::string& body) {
    return http::get(HOST, wstr(path), body, 20000, PORT);
}
static bool healthCheck() { std::string b; return httpGetText("/api/health", b); }

// ---------- 服务拉起（后台线程） ----------
static std::string findNode() {
    const char* candidates[] = {
        "C:\\Program Files\\nodejs\\node.exe",
        "C:\\Program Files (x86)\\nodejs\\node.exe",
        "C:\\nodejs\\node.exe",
        "D:\\nodejs\\node.exe",
    };
    for (auto c : candidates)
        if (GetFileAttributesA(c) != INVALID_FILE_ATTRIBUTES) return c;
    std::string pathEnv = getenv("PATH") ? getenv("PATH") : "";
    size_t p = 0;
    while (p <= pathEnv.size()) {
        size_t q = pathEnv.find(';', p);
        if (q == std::string::npos) q = pathEnv.size();
        std::string dir = pathEnv.substr(p, q - p);
        p = q + 1;
        if (dir.empty()) continue;
        std::string exe = dir + "\\node.exe";
        if (GetFileAttributesA(exe.c_str()) != INVALID_FILE_ATTRIBUTES) return exe;
    }
    return "";
}
static void ensureServerThread() {
    if (healthCheck()) { PostMessage(g_hWnd, WM_APP_SERVER_OK, 0, 0); return; }
    wchar_t buf[MAX_PATH]; GetModuleFileNameW(nullptr, buf, MAX_PATH);
    std::wstring exe = buf;
    size_t slash = exe.find_last_of(L"\\/");
    std::wstring dir = (slash == std::wstring::npos) ? L"." : exe.substr(0, slash);
    std::wstring serverJs;
    std::wstring cands[] = {
        dir + L"\\server\\src\\index.js",
        dir + L"\\..\\server\\src\\index.js",
    };
    for (auto& c : cands)
        if (GetFileAttributesW(c.c_str()) != INVALID_FILE_ATTRIBUTES) { serverJs = c; break; }
    std::string node = findNode();
    if (!node.empty() && !serverJs.empty()) {
        std::wstring wn = wstr(node);
        std::wstring cmd = L"\"" + wn + L"\" \"" + serverJs + L"\"";
        STARTUPINFOW si; ZeroMemory(&si, sizeof(si)); si.cb = sizeof(si);
        PROCESS_INFORMATION pi; ZeroMemory(&pi, sizeof(pi));
        std::vector<wchar_t> cmdBuf(cmd.begin(), cmd.end());
        cmdBuf.push_back(0);
        if (CreateProcessW(wn.c_str(), cmdBuf.data(), nullptr, nullptr, FALSE,
                           CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
            CloseHandle(pi.hThread); CloseHandle(pi.hProcess);
            for (int i = 0; i < 60; i++) {
                Sleep(250);
                if (healthCheck()) { PostMessage(g_hWnd, WM_APP_SERVER_OK, 0, 0); return; }
            }
        }
    }
    PostMessage(g_hWnd, WM_APP_SERVER_FAIL, 0, 0);
}

// ---------- 数据加载（后台线程） ----------
static void loadDataThread() {
    std::string body;
    if (httpGetText("/api/models?limit=5000", body)) {
        auto root = json::parse(body);
        auto items = root->get("items");
        if (items && items->isArray()) {
            std::vector<Model> tmp;
            tmp.reserve(items->arr.size());
            std::string maxUp;
            for (auto& it : items->arr) {
                Model m;
                m.id = it->getInt("id");
                m.brand = it->getStr("brand");
                m.category = it->getStr("category");
                m.model = it->getStr("model");
                m.price = it->getStr("price");
                m.note = it->getStr("note");
                m.updated_at = it->getStr("updated_at");
                std::string sn = seriesOf(m.brand, m.model);
                m.series = m.brand + "||" + (sn.empty() ? m.category : sn);
                if (m.updated_at > maxUp) maxUp = m.updated_at;
                auto vs = it->get("variants");
                if (vs && vs->isArray()) {
                    for (auto& v : vs->arr) {
                        Variant x;
                        x.spec = v->getStr("spec");
                        x.price = v->getStr("price");
                        if (!x.spec.empty()) m.variants.push_back(x);
                    }
                }
                tmp.push_back(std::move(m));
            }
            g_lastUpdated = maxUp;
            g_models = std::move(tmp);
            PostMessage(g_hWnd, WM_APP_DATA_READY, 0, 0);
            return;
        }
    }
    PostMessage(g_hWnd, WM_APP_DATA_READY, 1, 0);
}

// ---------- 过滤 / 排序 ----------
static long long priceOf(const Model& m) {
    size_t p = m.price.find_first_of("0123456789");
    if (p == std::string::npos) return -1;
    return atoll(m.price.c_str() + p);
}
static int colCmp(const Model& x, const Model& y) {
    if (g_sortCol == 0)      return strcmp((x.brand + x.model).c_str(), (y.brand + y.model).c_str());
    if (g_sortCol == 1)      return strcmp(specText(x).c_str(), specText(y).c_str());
    if (g_sortCol == 2) { long long p1 = priceOf(x), p2 = priceOf(y); return (p1 < p2) ? -1 : (p1 > p2 ? 1 : 0); }
    return strcmp(x.updated_at.c_str(), y.updated_at.c_str());
}
static void applyFilter() {
    g_view.clear();
    std::string q = trim(g_curSearch);
    for (size_t i = 0; i < g_models.size(); i++) {
        const Model& m = g_models[i];
        if (g_curBrand != "全部" && m.brand != g_curBrand) continue;
        if (!q.empty()) {
            std::string hay = m.brand + " " + m.model + " " + m.category + " " + m.note;
            if (hay.find(q) == std::string::npos) continue;
        }
        g_view.push_back(i);
    }
}
static void sortView() {
    // 系列永远升序（分组稳定），组内按排序列
    std::sort(g_view.begin(), g_view.end(), [](size_t a, size_t b) {
        const Model& x = g_models[a];
        const Model& y = g_models[b];
        // 组顺序：品牌优先级 → 品牌名 → 分类 → 组内排序列
        int ra = brandRank(x.brand), rb = brandRank(y.brand);
        if (ra != rb) return ra < rb;
        int cb = strcmp(x.brand.c_str(), y.brand.c_str());
        if (cb != 0) return cb < 0;
        int cs = strcmp(x.category.c_str(), y.category.c_str());
        if (cs != 0) return cs < 0;
        int c = colCmp(x, y);
        return g_sortDesc ? (c > 0) : (c < 0);
    });
}

// ---------- 列表填充（按系列分组） ----------
static void fillList() {
    ListView_RemoveAllGroups(g_hList);
    ListView_DeleteAllItems(g_hList);
    ListView_EnableGroupView(g_hList, TRUE);
    int groupId = 0;
    std::string lastSeries;
    for (size_t i = 0; i < g_view.size(); i++) {
        const Model& m = g_models[g_view[i]];
        if (m.series != lastSeries) {
            lastSeries = m.series;
            LVGROUP grp = {};
            grp.cbSize = sizeof(grp);
            grp.mask = LVGF_HEADER | LVGF_GROUPID | LVGF_STATE;
            grp.state = LVGS_NORMAL;
            grp.iGroupId = groupId;
            // 组头显示官方系列名（与手机端 App 首页一致）；非主流品牌回退显示分类
            std::string gname = m.series;
            size_t sep = gname.find("||");
            if (sep != std::string::npos) gname = gname.substr(sep + 2);
            if (gname.empty()) gname = m.category;
            std::wstring h = wstr(gname);
            grp.pszHeader = (LPWSTR)h.c_str();
            grp.cchHeader = (int)h.size();
            ListView_InsertGroup(g_hList, -1, &grp);
            groupId++;
        }
        LVITEMW li = {};
        li.mask = LVIF_TEXT | LVIF_GROUPID;
        li.iItem = (int)i;
        li.iGroupId = groupId - 1;
        std::wstring name = wstr(m.brand + " " + m.model);
        li.pszText = (LPWSTR)name.c_str();
        ListView_InsertItem(g_hList, &li);
        std::wstring sp = wstr(specText(m));
        ListView_SetItemText(g_hList, (int)i, 1, (LPWSTR)sp.c_str());
        std::wstring price = wstr(m.price);
        ListView_SetItemText(g_hList, (int)i, 2, (LPWSTR)price.c_str());
        std::wstring up = wstr(shortTime(m.updated_at));
        ListView_SetItemText(g_hList, (int)i, 3, (LPWSTR)up.c_str());
    }
    std::string st = "共 " + std::to_string(g_view.size()) + " 条";
    if (!g_lastUpdated.empty())
        st += "  ·  数据更新 " + (g_lastUpdated.size() >= 16 ? g_lastUpdated.substr(0, 16) : g_lastUpdated);
    SetWindowTextW(g_hStatus, wstr(st).c_str());
}

// ---------- 列表自绘 ----------
static LRESULT drawList(LPNMLVCUSTOMDRAW cd) {
    switch (cd->nmcd.dwDrawStage) {
    case CDDS_PREPAINT:
        return CDRF_NOTIFYITEMDRAW;
    case CDDS_ITEMPREPAINT: {
        int idx = (int)cd->nmcd.dwItemSpec;
        HDC hdc = cd->nmcd.hdc;
        HBRUSH b;
        if (idx >= 0 && idx < (int)g_view.size() && isToday(g_models[g_view[idx]].updated_at)) {
            RECT rc = cd->nmcd.rc;
            b = CreateSolidBrush(CLR_TODAY_BG);
            FillRect(hdc, &rc, b);
            DeleteObject(b);
            b = CreateSolidBrush(CLR_TODAY_BAR);
            RECT bar = { rc.left, rc.top, rc.left + 3, rc.bottom };
            FillRect(hdc, &bar, b);
            DeleteObject(b);
        } else {
            b = CreateSolidBrush(RGB(255, 255, 255));
            FillRect(hdc, &cd->nmcd.rc, b);
            DeleteObject(b);
        }
        return CDRF_NOTIFYSUBITEMDRAW;
    }
    case CDDS_SUBITEM | CDDS_ITEMPREPAINT: {
        int sub = cd->iSubItem;
        HDC hdc = cd->nmcd.hdc;
        RECT rc = cd->nmcd.rc;
        HPEN pen = CreatePen(PS_SOLID, 1, RGB(235, 239, 244));
        HPEN old = (HPEN)SelectObject(hdc, pen);
        MoveToEx(hdc, rc.left, rc.bottom - 1, nullptr);
        LineTo(hdc, rc.right, rc.bottom - 1);
        SelectObject(hdc, old);
        DeleteObject(pen);
        rc.left += 8; rc.right -= 4;
        int idx = (int)cd->nmcd.dwItemSpec;
        if (idx < 0 || idx >= (int)g_view.size()) return CDRF_DODEFAULT;
        const Model& m = g_models[g_view[idx]];
        SetBkMode(hdc, TRANSPARENT);
        if (sub == 0) {
            SetTextColor(hdc, CLR_TEXT);
            SelectObject(hdc, g_fontUI);
            DrawTextW(hdc, wstr(m.brand + " " + m.model).c_str(), -1, &rc, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        } else if (sub == 1) {
            SetTextColor(hdc, CLR_SUB);
            SelectObject(hdc, g_fontUI);
            DrawTextW(hdc, wstr(specText(m)).c_str(), -1, &rc, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        } else if (sub == 2) {
            SetTextColor(hdc, CLR_PRICE);
            SelectObject(hdc, g_fontBig);
            DrawTextW(hdc, wstr(m.price).c_str(), -1, &rc, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        } else {
            SetTextColor(hdc, CLR_SUB);
            SelectObject(hdc, g_fontSmall);
            DrawTextW(hdc, wstr(shortTime(m.updated_at)).c_str(), -1, &rc, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        }
        return CDRF_SKIPDEFAULT;
    }
    default:
        return CDRF_DODEFAULT;
    }
}

// ---------- 布局 ----------
static void layout(HWND hwnd) {
    RECT rc; GetClientRect(hwnd, &rc);
    int w = rc.right - rc.left;
    int h = rc.bottom - rc.top;
    MoveWindow(g_hSearch, 120, 10, w - 120 - 100, 28, TRUE);
    MoveWindow(g_hRefresh, w - 90, 8, 80, 32, TRUE);
    MoveWindow(g_btnPrev, w - BRAND_CTRL_W, TB_H + 7, 28, 26, TRUE);
    MoveWindow(g_btnNext, w - BRAND_CTRL_W + 34, TB_H + 7, 28, 26, TRUE);
    MoveWindow(g_hList, 8, TB_H + BRAND_H, w - 16, h - TB_H - BRAND_H - STATUS_H - 6, TRUE);
    MoveWindow(g_hStatus, 10, h - STATUS_H, w - 20, 20, TRUE);
    InvalidateRect(hwnd, nullptr, TRUE);
}

// ---------- 主窗口绘制（工具栏 + 品牌条，品牌条支持滚动） ----------
static void paintChrome(HDC hdc, RECT rc) {
    int w = rc.right - rc.left;
    RECT tb = { 0, 0, w, TB_H };
    HBRUSH b = CreateSolidBrush(RGB(255, 255, 255));
    FillRect(hdc, &tb, b);
    DeleteObject(b);
    HPEN pen = CreatePen(PS_SOLID, 1, CLR_BORDER);
    HPEN old = (HPEN)SelectObject(hdc, pen);
    HBRUSH o2 = (HBRUSH)SelectObject(hdc, GetStockObject(NULL_BRUSH));
    MoveToEx(hdc, 0, TB_H - 1, nullptr); LineTo(hdc, w, TB_H - 1);
    MoveToEx(hdc, 0, TB_H + BRAND_H - 1, nullptr); LineTo(hdc, w, TB_H + BRAND_H - 1);
    SelectObject(hdc, old);
    SelectObject(hdc, o2);
    DeleteObject(pen);
    SetBkMode(hdc, TRANSPARENT);
    SetTextColor(hdc, CLR_TEXT);
    SelectObject(hdc, g_fontUI);
    RECT tr = { 16, 0, 116, TB_H };
    DrawTextW(hdc, L"📱 手机回收查价", -1, &tr, DT_LEFT | DT_VCENTER | DT_SINGLELINE);
    // 品牌条（滚动绘制）
    int brandLeft = 16;
    int brandRight = w - BRAND_CTRL_W - 8;
    g_brandX.clear();
    int totalW = 0;
    for (size_t i = 0; i < g_brands.size(); i++) {
        g_brandX.push_back(brandLeft + totalW);
        totalW += (int)g_brands[i].size() * 16 + 22 + 8;
    }
    int maxScroll = totalW > (brandRight - brandLeft) ? totalW - (brandRight - brandLeft) : 0;
    if (g_scrollX > maxScroll) g_scrollX = maxScroll;
    if (g_scrollX < 0) g_scrollX = 0;
    for (size_t i = 0; i < g_brands.size(); i++) {
        const std::string& name = g_brands[i];
        int x = g_brandX[i] - g_scrollX;
        int tw = (int)name.size() * 16 + 22;
        if (x + tw < brandLeft || x > brandRight) continue;
        RECT br = { x, TB_H + 7, x + tw, TB_H + 7 + 26 };
        bool act = (name == g_curBrand);
        HBRUSH bb = CreateSolidBrush(act ? CLR_ACCENT : RGB(255, 255, 255));
        FillRect(hdc, &br, bb);
        DeleteObject(bb);
        if (!act) {
            HPEN p2 = CreatePen(PS_SOLID, 1, CLR_BORDER);
            HPEN o3 = (HPEN)SelectObject(hdc, p2);
            HBRUSH o4 = (HBRUSH)SelectObject(hdc, GetStockObject(NULL_BRUSH));
            Rectangle(hdc, br.left, br.top, br.right, br.bottom);
            SelectObject(hdc, o3);
            SelectObject(hdc, o4);
            DeleteObject(p2);
        }
        SetTextColor(hdc, act ? RGB(255, 255, 255) : CLR_TEXT);
        SelectObject(hdc, g_fontUI);
        RECT tr2 = br;
        DrawTextW(hdc, wstr(name).c_str(), -1, &tr2, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
    }
}

// 品牌条滚动（滚轮/箭头共用）
static void scrollBrands(int delta) {
    RECT rc; GetClientRect(g_hWnd, &rc);
    int brandLeft = 16;
    int brandRight = (rc.right - rc.left) - BRAND_CTRL_W - 8;
    int totalW = 0;
    for (auto& n : g_brands) totalW += (int)n.size() * 16 + 22 + 8;
    int maxScroll = totalW > (brandRight - brandLeft) ? totalW - (brandRight - brandLeft) : 0;
    g_scrollX += delta;
    if (g_scrollX > maxScroll) g_scrollX = maxScroll;
    if (g_scrollX < 0) g_scrollX = 0;
    RECT r = { 0, TB_H, rc.right, TB_H + BRAND_H };
    InvalidateRect(g_hWnd, &r, TRUE);
}

// ---------- 主窗口过程 ----------
static void refreshData();

static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_CREATE: {
        g_hWnd = hwnd;
        g_fontUI = CreateFontW(-16, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                               OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                               DEFAULT_PITCH | FF_DONTCARE, L"Microsoft YaHei UI");
        g_fontBig = CreateFontW(-20, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                                OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                                DEFAULT_PITCH | FF_DONTCARE, L"Microsoft YaHei UI");
        g_fontSmall = CreateFontW(-13, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                                  OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                                  DEFAULT_PITCH | FF_DONTCARE, L"Microsoft YaHei UI");
        g_hSearch = CreateWindowW(L"EDIT", L"", WS_CHILD | WS_VISIBLE | ES_AUTOHSCROLL | ES_LEFT | WS_BORDER,
                                  0, 0, 0, 0, hwnd, (HMENU)1001, GetModuleHandleW(nullptr), nullptr);
        SetWindowFont(g_hSearch, g_fontUI, TRUE);
        SendMessageW(g_hSearch, EM_SETCUEBANNER, TRUE, (LPARAM)L"搜索手机型号 / 品牌");
        g_hRefresh = CreateWindowW(L"BUTTON", L"⟳ 刷新", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
                                   0, 0, 0, 0, hwnd, (HMENU)1002, GetModuleHandleW(nullptr), nullptr);
        SetWindowFont(g_hRefresh, g_fontUI, TRUE);
        g_btnPrev = CreateWindowW(L"BUTTON", L"◀", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
                                  0, 0, 0, 0, hwnd, (HMENU)1010, GetModuleHandleW(nullptr), nullptr);
        SetWindowFont(g_btnPrev, g_fontUI, TRUE);
        g_btnNext = CreateWindowW(L"BUTTON", L"▶", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
                                  0, 0, 0, 0, hwnd, (HMENU)1011, GetModuleHandleW(nullptr), nullptr);
        SetWindowFont(g_btnNext, g_fontUI, TRUE);
        g_hList = CreateWindowExW(0, WC_LISTVIEWW, L"",
                                  WS_CHILD | WS_VISIBLE | LVS_REPORT | LVS_SHOWSELALWAYS | LVS_SINGLESEL,
                                  0, 0, 0, 0, hwnd, (HMENU)1003, GetModuleHandleW(nullptr), nullptr);
        ListView_SetExtendedListViewStyle(g_hList, LVS_EX_DOUBLEBUFFER | LVS_EX_FULLROWSELECT | LVS_EX_GRIDLINES);
        ListView_EnableGroupView(g_hList, TRUE);
        HIMAGELIST il = ImageList_Create(1, 26, ILC_COLOR, 1, 1);
        ListView_SetImageList(g_hList, il, LVSIL_SMALL);
        LVCOLUMNW col = {};
        col.mask = LVCF_TEXT | LVCF_WIDTH | LVCF_SUBITEM;
        col.cx = 320; col.iSubItem = 0; col.pszText = (LPWSTR)L"机型";
        ListView_InsertColumn(g_hList, 0, &col);
        col.cx = 170; col.iSubItem = 1; col.pszText = (LPWSTR)L"规格";
        ListView_InsertColumn(g_hList, 1, &col);
        col.cx = 110; col.iSubItem = 2; col.pszText = (LPWSTR)L"回收价";
        ListView_InsertColumn(g_hList, 2, &col);
        col.cx = 130; col.iSubItem = 3; col.pszText = (LPWSTR)L"更新";
        ListView_InsertColumn(g_hList, 3, &col);
        g_hStatus = CreateWindowW(L"STATIC", L"正在启动 API 服务…",
                                  WS_CHILD | WS_VISIBLE | SS_LEFT,
                                  0, 0, 0, 0, hwnd, (HMENU)1004, GetModuleHandleW(nullptr), nullptr);
        SetWindowFont(g_hStatus, g_fontSmall, TRUE);
        std::thread(ensureServerThread).detach();
        break;
    }
    case WM_SIZE: layout(hwnd); break;
    case WM_ERASEBKGND: {
        HDC hdc = (HDC)wp;
        RECT rc; GetClientRect(hwnd, &rc);
        HBRUSH b = CreateSolidBrush(CLR_BG);
        FillRect(hdc, &rc, b);
        DeleteObject(b);
        return 1;
    }
    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hwnd, &ps);
        RECT rc; GetClientRect(hwnd, &rc);
        HBRUSH b = CreateSolidBrush(CLR_BG);
        FillRect(hdc, &rc, b);
        DeleteObject(b);
        paintChrome(hdc, rc);
        EndPaint(hwnd, &ps);
        return 0;
    }
    case WM_APP_SERVER_OK:
        SetWindowTextW(g_hStatus, L"服务已就绪，加载数据…");
        refreshData();
        break;
    case WM_APP_SERVER_FAIL:
        SetWindowTextW(g_hStatus, L"无法连接 API 服务（请先启动 node server/src/index.js）");
        break;
    case WM_APP_DATA_READY: {
        g_loading = false;
        if (wp == 0) {
            applyFilter();
            sortView();
            g_brands.clear();
            g_brands.push_back("全部");
            for (auto& m : g_models)
                if (!m.brand.empty() && std::find(g_brands.begin(), g_brands.end(), m.brand) == g_brands.end())
                    g_brands.push_back(m.brand);
            std::sort(g_brands.begin() + 1, g_brands.end(), brandLess);
            g_scrollX = 0;
            layout(hwnd);
            fillList();
            InvalidateRect(hwnd, nullptr, TRUE);
        } else {
            SetWindowTextW(g_hStatus, L"数据加载失败，点击「刷新」重试");
        }
        break;
    }
    case WM_COMMAND:
        if (LOWORD(wp) == 1002) refreshData();
        else if (LOWORD(wp) == 1010) scrollBrands(-120);
        else if (LOWORD(wp) == 1011) scrollBrands(120);
        if (HIWORD(wp) == EN_CHANGE && LOWORD(wp) == 1001) {
            int n = GetWindowTextLengthW(g_hSearch) + 1;
            std::wstring w(n, 0);
            GetWindowTextW(g_hSearch, &w[0], n);
            int nb = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), -1, nullptr, 0, nullptr, nullptr);
            if (nb > 1) { g_curSearch.resize(nb - 1); WideCharToMultiByte(CP_UTF8, 0, w.c_str(), -1, &g_curSearch[0], nb, nullptr, nullptr); }
            applyFilter();
            sortView();
            fillList();
        }
        break;
    case WM_MOUSEWHEEL: {
        POINT pt = { GET_X_LPARAM(lp), GET_Y_LPARAM(lp) };
        ScreenToClient(hwnd, &pt);
        if (pt.y >= TB_H && pt.y <= TB_H + BRAND_H) {
            scrollBrands(-GET_WHEEL_DELTA_WPARAM(wp) / 120 * 60);
            return 0;
        }
        break;
    }
    case WM_LBUTTONDOWN: {
        int x = GET_X_LPARAM(lp), y = GET_Y_LPARAM(lp);
        if (y >= TB_H + 7 && y <= TB_H + 33 && !g_brandX.empty()) {
            int hx = x + g_scrollX;
            for (size_t i = 0; i < g_brandX.size(); i++) {
                int tw = (int)g_brands[i].size() * 16 + 22;
                if (hx >= g_brandX[i] && hx <= g_brandX[i] + tw) {
                    if (g_brands[i] != g_curBrand) {
                        g_curBrand = g_brands[i];
                        applyFilter();
                        sortView();
                        fillList();
                        InvalidateRect(hwnd, nullptr, TRUE);
                    }
                    break;
                }
            }
        }
        break;
    }
    case WM_NOTIFY: {
        NMHDR* h = (NMHDR*)lp;
        if (h->hwndFrom == g_hList) {
            if (h->code == NM_CUSTOMDRAW) return drawList((LPNMLVCUSTOMDRAW)lp);
            else if (h->code == LVN_COLUMNCLICK) {
                NMLISTVIEW* lv = (NMLISTVIEW*)lp;
                if (lv->iSubItem == g_sortCol) g_sortDesc = !g_sortDesc;
                else { g_sortCol = lv->iSubItem; g_sortDesc = (lv->iSubItem == 2); }
                sortView();
                fillList();
            } else if (h->code == NM_DBLCLK) {
                int sel = ListView_GetNextItem(g_hList, -1, LVNI_SELECTED);
                if (sel >= 0 && sel < (int)g_view.size()) {
                    const Model& m = g_models[g_view[sel]];
                    std::string msg = m.brand + " " + m.model;
                    if (!m.category.empty() && m.category != m.brand) msg += "（" + m.category + "）";
                    msg += "\n回收价：¥" + m.price;
                    std::string sp = specText(m);
                    if (!sp.empty()) msg += "\n规格：" + sp;
                    if (!m.note.empty()) msg += "\n备注：" + m.note;
                    msg += "\n更新：" + m.updated_at;
                    MessageBoxW(hwnd, wstr(msg).c_str(), L"机型详情", MB_OK | MB_ICONINFORMATION);
                }
            }
        }
        break;
    }
    case WM_CTLCOLORSTATIC: {
        HDC hdc = (HDC)wp;
        SetTextColor(hdc, CLR_SUB);
        SetBkMode(hdc, TRANSPARENT);
        return (LRESULT)GetStockObject(NULL_BRUSH);
    }
    case WM_DESTROY:
        PostQuitMessage(0);
        break;
    default:
        return DefWindowProcW(hwnd, msg, wp, lp);
    }
    return 0;
}

static void refreshData() {
    if (g_loading) return;
    g_loading = true;
    SetWindowTextW(g_hStatus, L"加载数据…");
    std::thread(loadDataThread).detach();
}

int WINAPI wWinMain(HINSTANCE hInst, HINSTANCE, PWSTR, int nCmdShow) {
    SetProcessDPIAware();
    INITCOMMONCONTROLSEX icc = { sizeof(icc), ICC_LISTVIEW_CLASSES | ICC_STANDARD_CLASSES };
    InitCommonControlsEx(&icc);
    WNDCLASSEXW wc = {};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInst;
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)GetStockObject(WHITE_BRUSH);
    wc.lpszClassName = L"PhoneRecycleCpp";
    RegisterClassExW(&wc);
    HWND hwnd = CreateWindowExW(0, wc.lpszClassName, L"手机回收查价 v1.1（C++ 轻量版）",
                                WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, 1100, 720,
                                nullptr, nullptr, hInst, nullptr);
    if (!hwnd) return 1;
    ShowWindow(hwnd, nCmdShow);
    UpdateWindow(hwnd);
    MSG msg;
    while (GetMessageW(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    return (int)msg.wParam;
}