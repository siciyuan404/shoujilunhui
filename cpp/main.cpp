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
#include <mutex>
#include <deque>
#include <map>
#include <set>
#include <gdiplus.h>
#include <shobjidl.h>
#include <shlwapi.h>

#include "json.h"
#include "http.h"

#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "shlwapi.lib")

// ---------- 常量 ----------
#define PORT 8760
const wchar_t* HOST = L"127.0.0.1";
#define APP_VERSION "2.3.1"        // 与 GitHub Release tag 保持一致
#define APP_VERSION_W L"2.3.1"
#define API_BASE "http://127.0.0.1:8760"          // 本地 API
const char* API_KEY = "sk-e756xogvi0lmt9miamt";   // 写操作鉴权（多端同步上传/删除）

#define WM_APP_SERVER_OK   (WM_APP + 1)
#define WM_APP_SERVER_FAIL (WM_APP + 2)
#define WM_APP_DATA_READY  (WM_APP + 3)
#define WM_APP_THUMB_READY (WM_APP + 6)
#define WM_APP_UPDATE_READY (WM_APP + 7)   // 新版本已下载就绪

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
    std::vector<std::string> images; // 图片 URL 列表（图集）
};

// ---------- 全局状态 ----------
HWND g_hWnd = nullptr;
HWND g_hSearch = nullptr;
HWND g_hList = nullptr;
HWND g_hStatus = nullptr;
HWND g_hRefresh = nullptr;
HWND g_hUpdateBtn = nullptr;   // 「更新」按钮（发现新版本时显示）
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
HFONT g_fontGal = nullptr;

// ---------- 图集状态 ----------
struct GalleryItem {
    std::wstring path;       // 本地文件路径（local=true 时有效）
    std::string data;        // 内存图片数据（下载/粘贴）
    bool local = false;      // 本地文件
    bool cloud = false;      // 云端用户图（gallery 表，可删）
    long long cloudId = 0;   // gallery 表记录 id
    Gdiplus::Image* img = nullptr;
};
// 调试日志
static void galLog(const char* s) {
    FILE* f = nullptr;
    fopen_s(&f, "gallery.log", "a");
    if (f) { fprintf(f, "%s\n", s); fclose(f); }
}
#include <dbghelp.h>
static LONG WINAPI crashDump(EXCEPTION_POINTERS* ep) {
    typedef BOOL(WINAPI* MiniDumpFn)(HANDLE, DWORD, HANDLE, DWORD, PMINIDUMP_EXCEPTION_INFORMATION, PMINIDUMP_USER_STREAM_INFORMATION, PMINIDUMP_CALLBACK_INFORMATION);
    HMODULE h = LoadLibraryW(L"dbghelp.dll");
    if (h) {
        auto fn = (MiniDumpFn)GetProcAddress(h, "MiniDumpWriteDump");
        if (fn) {
            HANDLE f = CreateFileW(L"crash.dmp", GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, 0, nullptr);
            if (f != INVALID_HANDLE_VALUE) {
                MINIDUMP_EXCEPTION_INFORMATION mei = { GetCurrentThreadId(), ep, FALSE };
                fn(GetCurrentProcess(), GetCurrentProcessId(), f, MiniDumpWithFullMemory, &mei, nullptr, nullptr);
                CloseHandle(f);
            }
        }
    }
    return EXCEPTION_EXECUTE_HANDLER;
}
struct GalleryState {
    HWND hwnd = nullptr;
    Model model;
    std::vector<GalleryItem> items;
    int cur = 0;
    int thumbScroll = 0;
};
static GalleryState g_gal;

// ---------- 列表图集列缩略图（懒加载 + 缓存） ----------
struct ThumbJob { long long id = 0; std::string url; };
static std::mutex g_thumbMtx;
static std::deque<ThumbJob> g_thumbQueue;
static std::map<long long, Gdiplus::Image*> g_thumbCache;  // 机型 id -> 缩略图
static std::deque<long long> g_thumbOrder;                 // FIFO 淘汰顺序
static std::set<long long> g_thumbQueued;                  // 已入队（防重复）
static std::map<int, RECT> g_copyBtns;                     // 型号列"复制"按钮：行索引 -> 按钮矩形
static const size_t THUMB_CACHE_MAX = 600;

// 从内存数据解码为 GDI+ 图像（返回新对象，失败返回 nullptr）
static Gdiplus::Image* decodeMemImg(const std::string& data) {
    HGLOBAL hg = GlobalAlloc(GMEM_MOVEABLE, data.size());
    if (!hg) return nullptr;
    void* mem = GlobalLock(hg);
    memcpy(mem, data.data(), data.size());
    GlobalUnlock(hg);
    Gdiplus::Image* img = nullptr;
    IStream* st = nullptr;
    if (CreateStreamOnHGlobal(hg, TRUE, &st) == S_OK) {
        img = new Gdiplus::Image(st);
        st->Release();
    } else GlobalFree(hg);
    if (img && img->GetLastStatus() != Gdiplus::Ok) { delete img; return nullptr; }
    return img;
}
// 取缩略图：命中缓存直接返回；未命中则入下载队列并返回 nullptr（由后台线程补）
static Gdiplus::Image* thumbOf(long long id, const std::string& url) {
    std::lock_guard<std::mutex> lk(g_thumbMtx);
    auto it = g_thumbCache.find(id);
    if (it != g_thumbCache.end()) return it->second;
    if (!g_thumbQueued.count(id)) { g_thumbQueued.insert(id); g_thumbQueue.push_back({ id, url }); }
    return nullptr;
}
// 后台缩略图下载线程
static void thumbThread() {
    for (;;) {
        ThumbJob job; bool has = false;
        {
            std::lock_guard<std::mutex> lk(g_thumbMtx);
            if (!g_thumbQueue.empty()) { job = g_thumbQueue.front(); g_thumbQueue.pop_front(); has = true; }
        }
        if (!has) { Sleep(150); continue; }
        std::string body;
        bool loaded = false;
        galLog(("thumb: try id=" + std::to_string(job.id) + " url=" + job.url).c_str());
        if (http::getUrl(job.url, body, 15000) && !body.empty()) {
            galLog(("thumb: got id=" + std::to_string(job.id) + " bytes=" + std::to_string(body.size())).c_str());
            Gdiplus::Image* img = decodeMemImg(body);
            if (img) {
                galLog(("thumb: decoded id=" + std::to_string(job.id)).c_str());
                std::lock_guard<std::mutex> lk(g_thumbMtx);
                if (g_thumbCache.size() >= THUMB_CACHE_MAX && !g_thumbOrder.empty()) {
                    long long oldest = g_thumbOrder.front(); g_thumbOrder.pop_front();
                    auto o = g_thumbCache.find(oldest);
                    if (o != g_thumbCache.end()) { delete o->second; g_thumbCache.erase(o); }
                }
                g_thumbCache[job.id] = img;
                g_thumbOrder.push_back(job.id);
                g_thumbQueued.erase(job.id);
                loaded = true;
            }
        }
        if (!loaded) {  // 失败允许重试
            galLog(("thumb: FAIL id=" + std::to_string(job.id) + " bodyLen=" + std::to_string(body.size())).c_str());
            std::lock_guard<std::mutex> lk(g_thumbMtx);
            g_thumbQueued.erase(job.id);
        }
        PostMessageW(g_hWnd, WM_APP_THUMB_READY, 0, 0);
    }
}
// 列表行内小缩略图（居中绘制）
static void drawListThumb(HDC hdc, Gdiplus::Image* img, int x, int y, int sz) {
    Gdiplus::Graphics g(hdc);
    g.SetInterpolationMode(Gdiplus::InterpolationModeHighQualityBicubic);
    int iw = (int)img->GetWidth(), ih = (int)img->GetHeight();
    if (iw <= 0 || ih <= 0) return;
    float sc = (float)std::min((sz - 2) / (float)iw, (sz - 2) / (float)ih);
    int dw = (int)(iw * sc), dh = (int)(ih * sc);
    if (dw < 1) dw = 1;
    if (dh < 1) dh = 1;
    g.DrawImage(img, x + (sz - dw) / 2, y + (sz - dh) / 2, dw, dh);
}

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

// ---------- 自动更新（静默检查 + 后台下载 + 重启替换，滚动生效） ----------
static std::string g_updateVer;          // 已就绪的新版本号
static std::atomic<bool> g_updateReady{false};

static std::vector<int> parseVer(const std::string& v) {
    std::vector<int> out;
    std::string s = v;
    if (!s.empty() && (s[0] == 'v' || s[0] == 'V')) s = s.substr(1);
    std::string cur;
    for (char c : s) {
        if (c >= '0' && c <= '9') cur += c;
        else if ((c == '.' || c == '-') && !cur.empty()) { out.push_back(atoi(cur.c_str())); cur.clear(); }
        else break;
    }
    if (!cur.empty()) out.push_back(atoi(cur.c_str()));
    return out;
}
static bool verNewer(const std::string& a, const std::string& b) {
    auto x = parseVer(a), y = parseVer(b);
    size_t n = std::max(x.size(), y.size());
    for (size_t i = 0; i < n; i++) {
        int xi = i < x.size() ? x[i] : 0, yi = i < y.size() ? y[i] : 0;
        if (xi != yi) return xi > yi;
    }
    return false;
}

// 后台线程：查 GitHub 最新 Release，有新版则静默下载 update.exe
static void updateThread() {
    Sleep(8000);
    std::string body;
    if (!http::getUrl("https://api.github.com/repos/siciyuan404/shoujilunhui/releases/latest", body, 15000)) return;
    std::string tag;
    size_t p = body.find("\"tag_name\":");
    if (p != std::string::npos) {
        p = body.find('"', p + 11);
        size_t p2 = body.find('"', p + 1);
        if (p2 != std::string::npos) tag = body.substr(p + 1, p2 - p - 1);
    }
    if (tag.empty()) return;
    if (!verNewer(tag, APP_VERSION)) return;   // 已是最新
    std::string dlUrl;
    p = 0;
    while ((p = body.find("\"browser_download_url\":", p)) != std::string::npos) {
        p += 24;
        size_t p2 = body.find('"', p);
        std::string u = body.substr(p, p2 - p);
        if (u.find("phone-recycle.exe") != std::string::npos) { dlUrl = u; break; }
        p = p2;
    }
    if (dlUrl.empty()) return;
    std::string bin;
    if (!http::getUrl(dlUrl, bin, 60000) || bin.size() < 100000) return;  // 完整性下限
    wchar_t buf[MAX_PATH]; GetModuleFileNameW(nullptr, buf, MAX_PATH);
    std::wstring exe = buf;
    size_t sl = exe.find_last_of(L"\\/");
    std::wstring upPath = exe.substr(0, sl) + L"\\phone-recycle.update.exe";
    HANDLE hf = CreateFileW(upPath.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (hf == INVALID_HANDLE_VALUE) return;
    DWORD w = 0;
    WriteFile(hf, bin.data(), (DWORD)bin.size(), &w, nullptr);
    CloseHandle(hf);
    g_updateVer = tag;
    g_updateReady = true;
    PostMessage(g_hWnd, WM_APP_UPDATE_READY, 0, 0);
}

// 生成 update.cmd 并以隐藏窗口运行：等本进程退出 → 用新 exe 覆盖自身 → 启动新版
static void restartToUpdate() {
    wchar_t buf[MAX_PATH]; GetModuleFileNameW(nullptr, buf, MAX_PATH);
    std::wstring exe = buf;
    size_t sl = exe.find_last_of(L"\\/");
    std::wstring dir = exe.substr(0, sl);
    std::wstring cmdFile = dir + L"\\update.cmd";
    char script[1024];
    snprintf(script, sizeof(script),
        "@echo off\r\n"
        "cd /d \"%s\"\r\n"
        "timeout /t 2 /nobreak >nul\r\n"
        "move /y \"phone-recycle.update.exe\" \"phone-recycle.exe\" >nul 2>&1\r\n"
        "start \"\" \"phone-recycle.exe\"\r\n"
        "del /q \"update.cmd\" >nul 2>&1\r\n",
        std::string(dir.begin(), dir.end()).c_str());
    int n = WideCharToMultiByte(CP_ACP, 0, cmdFile.c_str(), -1, nullptr, 0, nullptr, nullptr);
    std::string cmdA(n, 0);
    WideCharToMultiByte(CP_ACP, 0, cmdFile.c_str(), -1, &cmdA[0], n, nullptr, nullptr);
    HANDLE hf = CreateFileW(cmdFile.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (hf != INVALID_HANDLE_VALUE) {
        DWORD w = 0;
        WriteFile(hf, script, (DWORD)strlen(script), &w, nullptr);
        CloseHandle(hf);
    }
    ShellExecuteW(nullptr, L"open", L"cmd.exe", (L"/c \"" + cmdFile + L"\"").c_str(), dir.c_str(), SW_HIDE);
    PostQuitMessage(0);   // 退出当前进程，由 cmd 完成替换并启动新版
}

// 启动早期：若存在已下载的 update.exe 且版本更新，询问是否立即应用（滚动更新）
static void checkPendingUpdate() {
    galLog("checkPendingUpdate: enter");
    wchar_t buf[MAX_PATH]; GetModuleFileNameW(nullptr, buf, MAX_PATH);
    std::wstring exe = buf;
    size_t sl = exe.find_last_of(L"\\/");
    std::wstring dir = exe.substr(0, sl);
    std::wstring upPath = dir + L"\\phone-recycle.update.exe";
    if (GetFileAttributesW(upPath.c_str()) == INVALID_FILE_ATTRIBUTES) { galLog("checkPendingUpdate: no update.exe"); return; }
    galLog("checkPendingUpdate: found update.exe");
    int r = MessageBoxW(g_hWnd,
        L"检测到已下载的新版本安装包，是否现在应用并重启？\n（新版本将替换当前程序）",
        L"发现新版本", MB_YESNO | MB_ICONINFORMATION);
    if (r == IDYES) {
        restartToUpdate();
    } else if (r == IDNO) {
        DeleteFileW(upPath.c_str());   // 用户放弃 → 清除，避免每次启动都询问
    }
    // 其他返回值（弹窗异常等）→ 保留 update.exe，下次启动再提示
}

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
                auto ims = it->get("images");
                if (ims && ims->isArray())
                    for (auto& u : ims->arr) m.images.push_back(u->isString() ? u->str : "");
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
        std::wstring gc = m.images.empty() ? L"" : (std::to_wstring((int)m.images.size()) + L" 图");
        ListView_SetItemText(g_hList, (int)i, 4, (LPWSTR)gc.c_str());
    }
    std::string st = "共 " + std::to_string(g_view.size()) + " 条";
    if (!g_lastUpdated.empty())
        st += "  ·  数据更新 " + (g_lastUpdated.size() >= 16 ? g_lastUpdated.substr(0, 16) : g_lastUpdated);
    SetWindowTextW(g_hStatus, wstr(st).c_str());
}

// ---------- 列表自绘 ----------
static LRESULT drawList(LPNMLVCUSTOMDRAW cd) {
    DWORD ispec = cd->nmcd.dwItemSpec;
    if (ispec >= (DWORD)g_view.size()) return CDRF_DODEFAULT;
    int vidx = g_view[ispec];
    if (vidx < 0 || vidx >= (int)g_models.size()) return CDRF_DODEFAULT;
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
        DWORD isub = cd->nmcd.dwItemSpec;
        if (isub >= (DWORD)g_view.size()) return CDRF_DODEFAULT;
        int mi = g_view[isub];
        if (mi < 0 || mi >= (int)g_models.size()) return CDRF_DODEFAULT;
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
            // 型号文本（右侧预留"复制"按钮空间）
            RECT tr = rc; tr.right -= 64;
            SetTextColor(hdc, CLR_TEXT);
            SelectObject(hdc, g_fontUI);
            DrawTextW(hdc, wstr(m.brand + " " + m.model).c_str(), -1, &tr, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
            // 一键复制按钮
            RECT br = { rc.right - 60, rc.top + 3, rc.right - 4, rc.bottom - 3 };
            g_copyBtns[(int)cd->nmcd.dwItemSpec] = br;
            HBRUSH bbg = CreateSolidBrush(RGB(245, 247, 250));
            FillRect(hdc, &br, bbg);
            DeleteObject(bbg);
            HPEN pen = CreatePen(PS_SOLID, 1, RGB(200, 210, 225));
            HPEN oldp = (HPEN)SelectObject(hdc, pen);
            HBRUSH oldb = (HBRUSH)SelectObject(hdc, GetStockObject(NULL_BRUSH));
            RoundRect(hdc, br.left, br.top, br.right, br.bottom, 6, 6);
            SelectObject(hdc, oldp);
            SelectObject(hdc, oldb);
            DeleteObject(pen);
            SetTextColor(hdc, RGB(66, 133, 244));
            SelectObject(hdc, g_fontSmall);
            RECT lr = br; lr.left += 2; lr.right -= 2;
            DrawTextW(hdc, L"复制", -1, &lr, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
        } else if (sub == 1) {
            SetTextColor(hdc, CLR_SUB);
            SelectObject(hdc, g_fontUI);
            DrawTextW(hdc, wstr(specText(m)).c_str(), -1, &rc, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        } else if (sub == 2) {
            SetTextColor(hdc, CLR_PRICE);
            SelectObject(hdc, g_fontBig);
            DrawTextW(hdc, wstr(m.price).c_str(), -1, &rc, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        } else if (sub == 3) {
            SetTextColor(hdc, CLR_SUB);
            SelectObject(hdc, g_fontSmall);
            DrawTextW(hdc, wstr(shortTime(m.updated_at)).c_str(), -1, &rc, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        } else if (sub == 4) {
            // 图集列：有图显示第一张实例图，无图显示"无图"
            if (m.images.empty()) {
                SetTextColor(hdc, RGB(160, 170, 185));
                SelectObject(hdc, g_fontSmall);
                RECT tr2 = rc; tr2.left -= 4;
                DrawTextW(hdc, L"无图", -1, &tr2, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
            } else {
                Gdiplus::Image* th = thumbOf(m.id, m.images[0]);
                if (th) {
                    int sz = 22;
                    int x = rc.left + (rc.right - rc.left - sz) / 2;
                    int y = rc.top + (rc.bottom - rc.top - sz) / 2;
                    drawListThumb(hdc, th, x, y, sz);
                }
                // 未加载完成：留白，后台线程加载完发 WM_APP_THUMB_READY 重绘
            }
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


// ---------- 图集窗口 ----------
static std::wstring galBaseDir() {
    wchar_t buf[MAX_PATH]; GetModuleFileNameW(nullptr, buf, MAX_PATH);
    std::wstring d(buf);
    size_t pos = d.find_last_of(L"\\/");
    return d.substr(0, pos + 1) + L"gallery\\";
}
static std::wstring galModelDir() {
    return galBaseDir() + std::to_wstring(g_gal.model.id) + L"\\";
}
// 加载图片（本地文件或内存数据），缓存 img
static Gdiplus::Image* loadGalImage(const GalleryItem& it) {
    if (it.img) return it.img;
    Gdiplus::Image* img = nullptr;
    if (!it.path.empty()) img = new Gdiplus::Image(it.path.c_str());
    else if (!it.data.empty()) {
        HGLOBAL hg = GlobalAlloc(GMEM_MOVEABLE, it.data.size());
        if (hg) {
            void* mem = GlobalLock(hg);
            memcpy(mem, it.data.data(), it.data.size());
            GlobalUnlock(hg);
            IStream* st = nullptr;
            if (CreateStreamOnHGlobal(hg, TRUE, &st) == S_OK) {
                img = new Gdiplus::Image(st);
                st->Release();
            } else GlobalFree(hg);
        }
    }
    if (img && img->GetLastStatus() != Gdiplus::Ok) { delete img; return nullptr; }
    const_cast<GalleryItem&>(it).img = img;
    return img;
}
// 组装图集：本地收集照片（gallery\<id>\）+ 远程 images（内存加载）
static void loadGalleryItems() {
    for (auto& it : g_gal.items) if (it.img) { delete it.img; it.img = nullptr; }
    g_gal.items.clear();
    g_gal.cur = 0;
    g_gal.thumbScroll = 0;
    std::wstring base = galBaseDir();
    std::wstring dir = galModelDir();
    CreateDirectoryW(base.c_str(), nullptr);
    CreateDirectoryW(dir.c_str(), nullptr);
    WIN32_FIND_DATAW fd;
    HANDLE hf = FindFirstFileW((dir + L"*").c_str(), &fd);
    if (hf != INVALID_HANDLE_VALUE) {
        do {
            std::wstring fn = fd.cFileName;
            if (fn == L"." || fn == L"..") continue;
            std::wstring ext;
            size_t dp = fn.find_last_of(L'.');
            if (dp != std::wstring::npos) { ext = fn.substr(dp + 1); for (auto& c : ext) c = (wchar_t)towlower(c); }
            if (ext == L"jpg" || ext == L"jpeg" || ext == L"png" || ext == L"bmp" || ext == L"webp" || ext == L"gif") {
                GalleryItem it; it.path = dir + fn; it.local = true;
                g_gal.items.push_back(it);
            }
        } while (FindNextFileW(hf, &fd));
        FindClose(hf);
    }
    std::sort(g_gal.items.begin(), g_gal.items.end(),
              [](const GalleryItem& a, const GalleryItem& b) { return a.path < b.path; });
    galLog("loadGalleryItems: local scan done");
    for (auto& url : g_gal.model.images) {
        std::string body;
        if (http::getUrl(url, body, 20000) && !body.empty()) {
            GalleryItem it; it.local = false; it.data = std::move(body);
            g_gal.items.push_back(it);
        }
    }
    // 云端用户图（多端同步）：拉取本机型 gallery 记录，URL 经 server 读取（OSS 优先）
    std::string gbody;
    if (httpGetText("/api/gallery?model_id=" + std::to_string(g_gal.model.id), gbody)) {
        auto root = json::parse(gbody);
        auto items = root->get("items");
        if (items && items->isArray()) {
            for (auto& itp : items->arr) {
                std::string url = itp->getStr("url");
                long long gid = itp->getInt("id", 0);
                if (url.empty()) continue;
                std::string body;
                if (http::getUrl(std::string(API_BASE) + url, body, 20000) && !body.empty()) {
                    GalleryItem it; it.local = false; it.cloud = true; it.cloudId = gid; it.data = std::move(body);
                    g_gal.items.push_back(it);
                }
            }
        }
    }
    if (g_gal.cur >= (int)g_gal.items.size()) g_gal.cur = (int)g_gal.items.size() - 1;
}
// 等比例绘制大图
static void drawScaled(HDC hdc, Gdiplus::Image* img, RECT rc) {
    Gdiplus::Graphics g(hdc);
    g.SetInterpolationMode(Gdiplus::InterpolationModeHighQualityBicubic);
    int iw = (int)img->GetWidth(), ih = (int)img->GetHeight();
    if (iw <= 0 || ih <= 0) return;
    int rw = rc.right - rc.left, rh = rc.bottom - rc.top;
    float sc = (float)std::min(rw, rh) > 0 ? (float)std::min(rw / (float)iw, rh / (float)ih) : 1.0f;
    int dw = (int)(iw * sc), dh = (int)(ih * sc);
    int dx = rc.left + (rw - dw) / 2, dy = rc.top + (rh - dh) / 2;
    g.DrawImage(img, dx, dy, dw, dh);
}
// 绘制缩略图
static void drawThumb(HDC hdc, Gdiplus::Image* img, int x, int y, int sz, bool sel) {
    Gdiplus::Graphics g(hdc);
    g.SetInterpolationMode(Gdiplus::InterpolationModeHighQualityBicubic);
    Gdiplus::SolidBrush wb(Gdiplus::Color(255, 255, 255, 255));
    g.FillRectangle(&wb, x, y, sz, sz);
    int iw = (int)img->GetWidth(), ih = (int)img->GetHeight();
    if (iw > 0 && ih > 0) {
        float sc = (float)std::min((sz - 4) / (float)iw, (sz - 4) / (float)ih);
        int dw = (int)(iw * sc), dh = (int)(ih * sc);
        g.DrawImage(img, x + (sz - dw) / 2, y + (sz - dh) / 2, dw, dh);
    }
    if (sel) { Gdiplus::Pen pen(Gdiplus::Color(255, 22, 119, 255), 2.0f); g.DrawRectangle(&pen, x, y, sz, sz); }
    else { Gdiplus::Pen pen(Gdiplus::Color(255, 200, 210, 225), 1.0f); g.DrawRectangle(&pen, x, y, sz, sz); }
}
// 添加照片（多选文件对话框）
static void galleryAddPhotos() {
    IFileOpenDialog* dlg = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_FileOpenDialog, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&dlg));
    if (FAILED(hr) || !dlg) return;
    COMDLG_FILTERSPEC fs[] = {
        { L"图片文件", L"*.jpg;*.jpeg;*.png;*.bmp;*.webp;*.gif" },
        { L"所有文件", L"*.*" }
    };
    dlg->SetFileTypes(2, fs);
    DWORD opts = 0; dlg->GetOptions(&opts);
    dlg->SetOptions(opts | FOS_ALLOWMULTISELECT | FOS_FORCEFILESYSTEM | FOS_FILEMUSTEXIST);
    if (dlg->Show(g_gal.hwnd) == S_OK) {
        IShellItemArray* arr = nullptr;
        if (dlg->GetResults(&arr) == S_OK) {
            DWORD n = 0; arr->GetCount(&n);
            std::wstring dir = galModelDir();
            CreateDirectoryW(dir.c_str(), nullptr);
            for (DWORD i = 0; i < n; i++) {
                IShellItem* si = nullptr;
                if (arr->GetItemAt(i, &si) == S_OK) {
                    PWSTR p = nullptr;
                    if (si->GetDisplayName(SIGDN_FILESYSPATH, &p) == S_OK && p) {
                        std::wstring src(p);
                        std::wstring fn = src.substr(src.find_last_of(L"\\/") + 1);
                        std::wstring dst = dir + fn;
                        int k = 1;
                        while (GetFileAttributesW(dst.c_str()) != INVALID_FILE_ATTRIBUTES)
                            dst = dir + std::to_wstring(k++) + L"_" + fn;
                        CopyFileW(src.c_str(), dst.c_str(), FALSE);
                        CoTaskMemFree(p);
                    }
                    si->Release();
                }
            }
            arr->Release();
        }
    }
    dlg->Release();
    loadGalleryItems();
    if (g_gal.hwnd) InvalidateRect(g_gal.hwnd, nullptr, TRUE);
}
// 删除当前（仅本地照片）
static void galleryDelete() {
    if (g_gal.items.empty()) return;
    int idx = g_gal.cur;
    if (idx < 0 || idx >= (int)g_gal.items.size()) return;
    GalleryItem& it = g_gal.items[idx];
    if (it.cloud) {
        // 云端用户图：删除 gallery 记录（多端同步删除）
        std::string url = std::string(API_BASE) + "/api/gallery/" + std::to_string(it.cloudId);
        std::string resp;
        if (http::request(url, "DELETE", "", "", "X-API-Key: " + std::string(API_KEY) + "\r\n", resp, 15000) == 200) {
            galLog("galleryDelete: cloud deleted");
            loadGalleryItems();
            if (g_gal.hwnd) InvalidateRect(g_gal.hwnd, nullptr, TRUE);
        } else {
            MessageBoxW(g_gal.hwnd, L"云端删除失败（请检查服务连接）", L"提示", MB_OK);
        }
        return;
    }
    if (!it.local) { MessageBoxW(g_gal.hwnd, L"官方图片不可删除（由服务器管理）", L"提示", MB_OK); return; }
    // GDI+ 图片对象会锁定文件句柄，必须先释放再删除，否则 DeleteFileW 失败
    if (it.img) { delete it.img; it.img = nullptr; }
    if (DeleteFileW(it.path.c_str())) {
        galLog("galleryDelete: deleted");
        loadGalleryItems();
        if (g_gal.hwnd) InvalidateRect(g_gal.hwnd, nullptr, TRUE);
    } else {
        galLog("galleryDelete: DeleteFileW FAIL");
        MessageBoxW(g_gal.hwnd, L"删除失败（文件可能正被占用）", L"提示", MB_OK);
    }
}
// 上传本地图到云端（POST /api/gallery?model_id=，OSS 优先），成功则删除本地副本（云端权威）
static bool uploadLocalToCloud(const std::wstring& path) {
    HANDLE hf = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, 0, nullptr);
    if (hf == INVALID_HANDLE_VALUE) return false;
    DWORD sz = GetFileSize(hf, nullptr);
    if (sz == INVALID_FILE_SIZE || sz == 0 || sz > 20 * 1024 * 1024) { CloseHandle(hf); return false; }
    std::string data(sz, 0);
    DWORD rd = 0;
    ReadFile(hf, &data[0], sz, &rd, nullptr);
    CloseHandle(hf);
    if (rd != sz) return false;
    std::string ct = "image/jpeg";
    std::wstring ext;
    size_t dp = path.find_last_of(L'.');
    if (dp != std::wstring::npos) { ext = path.substr(dp + 1); for (auto& c : ext) c = (wchar_t)towlower(c); }
    if (ext == L"png") ct = "image/png";
    else if (ext == L"bmp") ct = "image/bmp";
    else if (ext == L"webp") ct = "image/webp";
    else if (ext == L"gif") ct = "image/gif";
    std::string url = std::string(API_BASE) + "/api/gallery?model_id=" + std::to_string(g_gal.model.id);
    std::string resp;
    if (!http::postUrl(url, data, ct, API_KEY, resp, 60000)) {
        galLog("uploadLocalToCloud: POST FAIL");
        return false;
    }
    auto root = json::parse(resp);
    auto okp = root->get("ok");
    if (!okp || !okp->b) { galLog("uploadLocalToCloud: resp not ok"); return false; }
    DeleteFileW(path.c_str());   // 已上云，删除本地副本，图集以云端为准
    galLog("uploadLocalToCloud: OK -> cloud");
    return true;
}

// 从剪贴板粘贴图片（支持：资源管理器复制的图片文件 CF_HDROP、截图/复制图像 CF_DIB）
static void galleryPaste() {
    galLog("galleryPaste: enter");
    if (!OpenClipboard(g_gal.hwnd)) { galLog("galleryPaste: OpenClipboard FAIL"); return; }
    bool ok = false;
    std::vector<std::wstring> saved;   // 粘贴保存成功的本地文件
    std::wstring dir = galModelDir();
    // 1) 剪贴板中是图片文件
    if (IsClipboardFormatAvailable(CF_HDROP)) {
        HDROP drop = (HDROP)GetClipboardData(CF_HDROP);
        if (drop) {
            UINT n = DragQueryFileW(drop, 0xFFFFFFFF, nullptr, 0);
            CreateDirectoryW(dir.c_str(), nullptr);
            for (UINT i = 0; i < n; i++) {
                wchar_t p[MAX_PATH] = { 0 };
                DragQueryFileW(drop, i, p, MAX_PATH);
                std::wstring src(p);
                if (src.empty()) continue;
                std::wstring ext;
                size_t dp = src.find_last_of(L'.');
                if (dp != std::wstring::npos) { ext = src.substr(dp + 1); for (auto& c : ext) c = (wchar_t)towlower(c); }
                if (ext != L"jpg" && ext != L"jpeg" && ext != L"png" && ext != L"bmp" && ext != L"webp" && ext != L"gif") continue;
                std::wstring fn = src.substr(src.find_last_of(L"\\/") + 1);
                std::wstring dst = dir + fn;
                int k = 1;
                while (GetFileAttributesW(dst.c_str()) != INVALID_FILE_ATTRIBUTES)
                    dst = dir + std::to_wstring(k++) + L"_" + fn;
                if (CopyFileW(src.c_str(), dst.c_str(), FALSE)) { ok = true; saved.push_back(dst); }
            }
        }
    }
    // 2) 剪贴板中是位图（截图等）
    if (!ok && IsClipboardFormatAvailable(CF_DIB)) {
        HANDLE h = GetClipboardData(CF_DIB);
        if (h) {
            BITMAPINFO* bi = (BITMAPINFO*)GlobalLock(h);
            if (bi && bi->bmiHeader.biSize >= sizeof(BITMAPINFOHEADER)) {
                DWORD clr = (bi->bmiHeader.biBitCount <= 8) ? (1u << bi->bmiHeader.biBitCount) : 0u;
                if (bi->bmiHeader.biClrUsed) clr = bi->bmiHeader.biClrUsed;
                DWORD off = bi->bmiHeader.biSize;
                if (bi->bmiHeader.biCompression == BI_BITFIELDS) off += 3 * sizeof(DWORD);
                const BYTE* bits = (const BYTE*)bi + off + clr * sizeof(RGBQUAD);
                HDC hdc = GetDC(g_gal.hwnd);
                HBITMAP hbm = CreateDIBitmap(hdc, &bi->bmiHeader, CBM_INIT, bits, bi, DIB_RGB_COLORS);
                ReleaseDC(g_gal.hwnd, hdc);
                if (hbm) {
                    Gdiplus::Bitmap bmp(hbm, (HPALETTE)nullptr);
                    if (bmp.GetLastStatus() == Gdiplus::Ok) {
                        CreateDirectoryW(dir.c_str(), nullptr);
                        SYSTEMTIME st; GetLocalTime(&st);
                        wchar_t base[64];
                        swprintf(base, 64, L"粘贴_%04d%02d%02d_%02d%02d%02d.png", st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond);
                        std::wstring dst = dir + base;
                        int k = 1;
                        while (GetFileAttributesW(dst.c_str()) != INVALID_FILE_ATTRIBUTES)
                            dst = dir + std::to_wstring(k++) + L"_" + base;
                        CLSID pngClsid;
                        CLSIDFromString(L"{557cf406-1a04-11d3-9a73-0000f81ef32e}", &pngClsid); // image/png
                        if (bmp.Save(dst.c_str(), &pngClsid, nullptr) == Gdiplus::Ok) { ok = true; saved.push_back(dst); }
                    }
                    DeleteObject(hbm);
                }
            }
            GlobalUnlock(h);
        }
    }
    CloseClipboard();
    if (ok) {
        // 多端同步：上传云端（成功则删本地副本，云端权威）
        int upOk = 0;
        for (auto& f : saved) if (uploadLocalToCloud(f)) upOk++;
        galLog(("galleryPaste: uploaded " + std::to_string(upOk) + "/" + std::to_string(saved.size())).c_str());
        loadGalleryItems();
        if (g_gal.hwnd) InvalidateRect(g_gal.hwnd, nullptr, TRUE);
        if (upOk > 0) {
            std::wstring msg = L"已粘贴并同步到云端 " + std::to_wstring(upOk) + L" 张（其他设备打开该机型图集可见）";
            if (upOk < (int)saved.size()) msg += L"\n" + std::to_wstring((int)saved.size() - upOk) + L" 张上传失败，已保留在本地";
            MessageBoxW(g_gal.hwnd, msg.c_str(), L"提示", MB_OK);
        }
    } else {
        galLog("galleryPaste: nothing usable");
        MessageBoxW(g_gal.hwnd, L"剪贴板中没有可粘贴的图片（可复制图片文件或截图后再粘贴）", L"提示", MB_OK);
    }
}
static LRESULT CALLBACK GalleryWndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_CREATE:
        CreateWindowW(L"BUTTON", L"＋ 添加照片", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 16, 560, 110, 30, hwnd, (HMENU)2001, GetModuleHandleW(nullptr), nullptr);
        CreateWindowW(L"BUTTON", L"删除", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 136, 560, 70, 30, hwnd, (HMENU)2002, GetModuleHandleW(nullptr), nullptr);
        CreateWindowW(L"BUTTON", L"◀ 上一张", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 330, 560, 100, 30, hwnd, (HMENU)2003, GetModuleHandleW(nullptr), nullptr);
        CreateWindowW(L"BUTTON", L"下一张 ▶", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 440, 560, 100, 30, hwnd, (HMENU)2004, GetModuleHandleW(nullptr), nullptr);
        CreateWindowW(L"BUTTON", L"关闭", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 760, 560, 70, 30, hwnd, (HMENU)2005, GetModuleHandleW(nullptr), nullptr);
        SetFocus(hwnd);   // 窗口本体拿焦点，便于 Ctrl+V 粘贴
        break;
    case WM_PAINT: {
        PAINTSTRUCT ps; HDC hdc = BeginPaint(hwnd, &ps);
        RECT rc; GetClientRect(hwnd, &rc);
        HBRUSH wb = CreateSolidBrush(RGB(255, 255, 255));
        FillRect(hdc, &rc, wb); DeleteObject(wb);
        int nLocal = 0, nCloud = 0; for (auto& it : g_gal.items) { if (it.local) nLocal++; if (it.cloud) nCloud++; }
        std::wstring t = http::utf8ToWide(g_gal.model.brand + " " + g_gal.model.model);
        t += L" — 图集（共 " + std::to_wstring((int)g_gal.items.size()) + L" 张，云端 " + std::to_wstring(nCloud) + L"，本地 " + std::to_wstring(nLocal) + L"）";
        SetWindowTextW(hwnd, t.c_str());
        // 大图预览区
        RECT prev = { 16, 44, rc.right - 16, 464 };
        if (g_gal.items.empty()) {
            SetTextColor(hdc, RGB(150, 158, 172));
            SetBkMode(hdc, TRANSPARENT);
            SelectObject(hdc, g_fontUI);
            RECT tr = prev;
            DrawTextW(hdc, L"暂无图片 — 点击「添加照片」上传你收集的图片", -1, &tr, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
        } else {
            int idx = std::max(0, std::min(g_gal.cur, (int)g_gal.items.size() - 1));
            Gdiplus::Image* img = loadGalImage(g_gal.items[idx]);
            if (img) drawScaled(hdc, img, prev);
            else { SetTextColor(hdc, RGB(150, 158, 172)); SetBkMode(hdc, TRANSPARENT); SelectObject(hdc, g_fontUI); RECT tr = prev; DrawTextW(hdc, L"图片加载失败", -1, &tr, DT_CENTER | DT_VCENTER | DT_SINGLELINE); }
        }
        // 缩略图条
        int cell = 72, tsx = 16 - g_gal.thumbScroll;
        for (size_t i = 0; i < g_gal.items.size(); i++) {
            if (tsx + 64 < 16) { tsx += cell; continue; }
            if (tsx > rc.right - 16) break;
            Gdiplus::Image* t = loadGalImage(g_gal.items[i]);
            if (t) drawThumb(hdc, t, tsx, 478, 64, (int)i == g_gal.cur);
            tsx += cell;
        }
        EndPaint(hwnd, &ps);
        break;
    }
    case WM_KEYDOWN:
        // Ctrl+V 粘贴图片（截图 / 复制的图片文件）
        if (wp == 'V' && (GetAsyncKeyState(VK_CONTROL) & 0x8000)) galleryPaste();
        break;
    case WM_LBUTTONDOWN: {
        SetFocus(hwnd);   // 点击窗口内任意处后焦点回窗口，保证下次 Ctrl+V 生效
        int x = GET_X_LPARAM(lp), y = GET_Y_LPARAM(lp);
        if (y >= 478 && y <= 554) {
            int idx = (x - 16 + g_gal.thumbScroll) / 72;
            if (idx >= 0 && idx < (int)g_gal.items.size()) {
                g_gal.cur = idx;
                InvalidateRect(hwnd, nullptr, TRUE);
            }
        }
        break;
    }
    case WM_MOUSEWHEEL: {
        POINT pt = { GET_X_LPARAM(lp), GET_Y_LPARAM(lp) };
        ScreenToClient(hwnd, &pt);
        if (pt.y >= 478 && pt.y <= 554) {
            RECT rc; GetClientRect(hwnd, &rc);
            int visible = ((rc.right - rc.left - 32) / 72) * 72;
            int maxScroll = (int)(g_gal.items.size() * 72) - visible;
            g_gal.thumbScroll += GET_WHEEL_DELTA_WPARAM(wp) / 120 * 36;
            if (g_gal.thumbScroll < 0) g_gal.thumbScroll = 0;
            if (g_gal.thumbScroll > maxScroll && maxScroll > 0) g_gal.thumbScroll = maxScroll;
            InvalidateRect(hwnd, nullptr, TRUE);
        }
        break;
    }
    case WM_COMMAND:
        switch (LOWORD(wp)) {
        case 2001: galleryAddPhotos(); break;
        case 2002: galleryDelete(); break;
        case 2003:
            if (!g_gal.items.empty()) { g_gal.cur = (g_gal.cur - 1 + (int)g_gal.items.size()) % (int)g_gal.items.size(); InvalidateRect(hwnd, nullptr, TRUE); }
            break;
        case 2004:
            if (!g_gal.items.empty()) { g_gal.cur = (g_gal.cur + 1) % (int)g_gal.items.size(); InvalidateRect(hwnd, nullptr, TRUE); }
            break;
        case 2005: DestroyWindow(hwnd); break;
        }
        if (LOWORD(wp) != 2005) SetFocus(hwnd);   // 点击按钮后焦点回窗口，便于连续粘贴
        break;
    case WM_CLOSE:
        DestroyWindow(hwnd);
        break;
    case WM_DESTROY:
        g_gal.hwnd = nullptr;
        for (auto& it : g_gal.items) if (it.img) delete it.img;
        g_gal.items.clear();
        break;
    default:
        return DefWindowProcW(hwnd, msg, wp, lp);
    }
    return 0;
}
// 打开某机型的图集窗口
static void openGallery(const Model& m) {
    g_gal.model = m;
    if (g_gal.hwnd) { DestroyWindow(g_gal.hwnd); g_gal.hwnd = nullptr; }
    g_gal.hwnd = CreateWindowExW(0, L"PhoneRecycleGallery", L"机型图集",
                                 WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, 880, 610,
                                 nullptr, nullptr, GetModuleHandleW(nullptr), nullptr);
    if (!g_gal.hwnd) { galLog("openGallery: CreateWindowExW FAILED"); return; }
    galLog("openGallery: window created");
    ShowWindow(g_gal.hwnd, SW_SHOW);
    SetForegroundWindow(g_gal.hwnd);
    galLog("openGallery: shown, loading items");
    loadGalleryItems();
    galLog("openGallery: items loaded, invalidate");
    InvalidateRect(g_gal.hwnd, nullptr, TRUE);
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
        g_hUpdateBtn = CreateWindowW(L"BUTTON", L"⬆ 更新", WS_CHILD | BS_PUSHBUTTON,
                                     0, 0, 0, 0, hwnd, (HMENU)1005, GetModuleHandleW(nullptr), nullptr);
        SetWindowFont(g_hUpdateBtn, g_fontUI, TRUE);
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
        col.cx = 78; col.iSubItem = 4; col.pszText = (LPWSTR)L"图集";
        ListView_InsertColumn(g_hList, 4, &col);
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
    case WM_APP_THUMB_READY:
        // 列表缩略图后台加载完成 → 重绘列表
        if (g_hList) InvalidateRect(g_hList, nullptr, TRUE);
        break;
    case WM_APP_UPDATE_READY: {
        // 新版本已静默下载：显示「更新」按钮并提示
        std::wstring st = L"已下载新版本 v";
        st += http::utf8ToWide(g_updateVer);
        st += L"，点击「⬆ 更新」重启生效";
        SetWindowTextW(g_hStatus, st.c_str());
        if (g_hUpdateBtn) ShowWindow(g_hUpdateBtn, SW_SHOW);
        layout(hwnd);
        break;
    }
    case WM_COMMAND:
        if (LOWORD(wp) == 1002) refreshData();
        else if (LOWORD(wp) == 1010) scrollBrands(-120);
        else if (LOWORD(wp) == 1011) scrollBrands(120);
        else if (LOWORD(wp) == 1005) restartToUpdate();
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
            } else if (h->code == NM_CLICK) {
                // 单击"复制"按钮 → 一键复制型号；单击"图集"列 → 展开图集
                POINT pt; GetCursorPos(&pt);
                ScreenToClient(g_hList, &pt);
                LVHITTESTINFO ht = {};
                ht.pt = pt;
                ListView_SubItemHitTest(g_hList, &ht);
                if (ht.iItem >= 0 && ht.iItem < (int)g_view.size()) {
                    auto bit = g_copyBtns.find(ht.iItem);
                    if (bit != g_copyBtns.end() && ht.iSubItem == 0) {
                        RECT br = bit->second;
                        if (pt.x >= br.left && pt.x <= br.right && pt.y >= br.top && pt.y <= br.bottom) {
                            wchar_t buf[512];
                            ListView_GetItemText(g_hList, ht.iItem, 0, buf, 512);
                            if (OpenClipboard(hwnd)) {
                                EmptyClipboard();
                                int cl = (int)wcslen(buf);
                                HGLOBAL hg = GlobalAlloc(GMEM_MOVEABLE, (cl + 1) * sizeof(wchar_t));
                                if (hg) {
                                    wchar_t* p = (wchar_t*)GlobalLock(hg);
                                    if (p) { wcscpy_s(p, cl + 1, buf); GlobalUnlock(hg); }
                                    SetClipboardData(CF_UNICODETEXT, hg);
                                }
                                CloseClipboard();
                                std::wstring st = L"已复制型号：" + std::wstring(buf);
                                SetWindowTextW(g_hStatus, st.c_str());
                            }
                            break;   // 已处理，不再走图集列逻辑
                        }
                    }
                }
                if (ht.iItem >= 0 && ht.iSubItem == 4 && ht.iItem < (int)g_view.size()) {
                    const Model& m = g_models[g_view[ht.iItem]];
                    openGallery(m);
                }
            } else if (h->code == NM_RETURN) {
                int sel = ListView_GetNextItem(g_hList, -1, LVNI_SELECTED);
                if (sel >= 0 && sel < (int)g_view.size()) {
                    const Model& m = g_models[g_view[sel]];
                    openGallery(m);
                }
            } else if (h->code == NM_DBLCLK) {
                galLog("NM_DBLCLK received");
                int sel = ListView_GetNextItem(g_hList, -1, LVNI_SELECTED);
                if (sel >= 0 && sel < (int)g_view.size()) {
                    const Model& m = g_models[g_view[sel]];
                    galLog(("NM_DBLCLK sel=" + std::to_string(sel)).c_str());
                    openGallery(m);
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
    SetUnhandledExceptionFilter(crashDump);
    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    Gdiplus::GdiplusStartupInput gsi;
    ULONG_PTR gdiToken = 0;
    Gdiplus::GdiplusStartup(&gdiToken, &gsi, nullptr);
    std::thread(thumbThread).detach();   // 列表图集列缩略图后台加载
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
    WNDCLASSEXW wg = {};
    wg.cbSize = sizeof(wg);
    wg.lpfnWndProc = GalleryWndProc;
    wg.hInstance = hInst;
    wg.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wg.hbrBackground = (HBRUSH)GetStockObject(WHITE_BRUSH);
    wg.lpszClassName = L"PhoneRecycleGallery";
    RegisterClassExW(&wg);
    HWND hwnd = CreateWindowExW(0, wc.lpszClassName, L"手机回收查价 v" APP_VERSION_W L"（C++ 轻量版）",
                                WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, 1100, 720,
                                nullptr, nullptr, hInst, nullptr);
    if (!hwnd) return 1;
    ShowWindow(hwnd, nCmdShow);
    UpdateWindow(hwnd);
    checkPendingUpdate();            // 滚动更新：应用已下载的新版本（若用户确认）
    std::thread(updateThread).detach();   // 静默检查 GitHub 最新版并后台下载
    MSG msg;
    while (GetMessageW(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    if (gdiToken) Gdiplus::GdiplusShutdown(gdiToken);
    CoUninitialize();
    return (int)msg.wParam;
}