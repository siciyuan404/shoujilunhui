// 轻量 HTTP GET 客户端（WinHTTP，系统自带，零依赖）
#pragma once
#include <string>
#include <windows.h>
#include <winhttp.h>
#pragma comment(lib, "winhttp.lib")

namespace http {

// 同步 GET，成功返回 true 并填充 body；超时 ms
inline bool get(const std::wstring& host, const std::wstring& path, std::string& body, int timeoutMs = 15000, int port = 80) {
    body.clear();
    HINTERNET hSession = WinHttpOpen(L"PhoneRecycleCpp/1.0", WINHTTP_ACCESS_TYPE_NO_PROXY,
                                     WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
    if (!hSession) return false;
    HINTERNET hConn = WinHttpConnect(hSession, host.c_str(), (INTERNET_PORT)port, 0);
    bool ok = false;
    if (hConn) {
        HINTERNET hReq = WinHttpOpenRequest(hConn, L"GET", path.c_str(), nullptr, WINHTTP_NO_REFERER,
                                            WINHTTP_DEFAULT_ACCEPT_TYPES, 0);
        if (hReq) {
            // 设超时（解析/连接/发送/接收）
            DWORD to = (DWORD)timeoutMs;
            WinHttpSetTimeouts(hSession, to, to, to, to);
            if (WinHttpSendRequest(hReq, WINHTTP_NO_ADDITIONAL_HEADERS, 0, WINHTTP_NO_REQUEST_DATA, 0, 0, 0) &&
                WinHttpReceiveResponse(hReq, nullptr)) {
                DWORD status = 0;
                DWORD statusLen = sizeof(status);
                WinHttpQueryHeaders(hReq, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                                    WINHTTP_HEADER_NAME_BY_INDEX, &status, &statusLen, WINHTTP_NO_HEADER_INDEX);
                if (status == 200) {
                    DWORD avail = 0;
                    while (WinHttpQueryDataAvailable(hReq, &avail) && avail > 0) {
                        std::string chunk;
                        chunk.resize(avail);
                        DWORD read = 0;
                        if (!WinHttpReadData(hReq, &chunk[0], avail, &read)) break;
                        chunk.resize(read);
                        body += chunk;
                    }
                    ok = true;
                }
            }
            WinHttpCloseHandle(hReq);
        }
        WinHttpCloseHandle(hConn);
    }
    WinHttpCloseHandle(hSession);
    return ok;
}

// 按完整 URL 同步 GET（支持 http/https，自动解析 host/port/路径）
inline std::wstring utf8ToWide(const std::string& s);  // 前向声明（定义在本文件下方）
inline bool getUrl(const std::string& url, std::string& body, int timeoutMs = 30000) {
    body.clear();
    bool secure = false;
    std::string rest;
    if (url.compare(0, 8, "https://") == 0) { secure = true; rest = url.substr(8); }
    else if (url.compare(0, 7, "http://") == 0) { rest = url.substr(7); }
    else return false;
    size_t slash = rest.find('/');
    std::string hostpart = (slash == std::string::npos) ? rest : rest.substr(0, slash);
    std::string path = (slash == std::string::npos) ? "/" : rest.substr(slash);
    int port = secure ? 443 : 80;
    size_t colon = hostpart.rfind(':');
    if (colon != std::string::npos) {
        port = atoi(hostpart.substr(colon + 1).c_str());
        hostpart = hostpart.substr(0, colon);
    }
    HINTERNET hSession = WinHttpOpen(L"PhoneRecycleCpp/1.0", WINHTTP_ACCESS_TYPE_NO_PROXY,
                                     WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
    if (!hSession) return false;
    HINTERNET hConn = WinHttpConnect(hSession, utf8ToWide(hostpart).c_str(), (INTERNET_PORT)port, 0);
    bool ok = false;
    if (hConn) {
        HINTERNET hReq = WinHttpOpenRequest(hConn, L"GET", utf8ToWide(path).c_str(), nullptr, WINHTTP_NO_REFERER,
                                            WINHTTP_DEFAULT_ACCEPT_TYPES, secure ? WINHTTP_FLAG_SECURE : 0);
        if (hReq) {
            DWORD to = (DWORD)timeoutMs;
            WinHttpSetTimeouts(hSession, to, to, to, to);
            if (WinHttpSendRequest(hReq, WINHTTP_NO_ADDITIONAL_HEADERS, 0, WINHTTP_NO_REQUEST_DATA, 0, 0, 0) &&
                WinHttpReceiveResponse(hReq, nullptr)) {
                DWORD status = 0;
                DWORD statusLen = sizeof(status);
                WinHttpQueryHeaders(hReq, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                                    WINHTTP_HEADER_NAME_BY_INDEX, &status, &statusLen, WINHTTP_NO_HEADER_INDEX);
                if (status == 200) {
                    DWORD avail = 0;
                    while (WinHttpQueryDataAvailable(hReq, &avail) && avail > 0) {
                        std::string chunk;
                        chunk.resize(avail);
                        DWORD read = 0;
                        if (!WinHttpReadData(hReq, &chunk[0], avail, &read)) break;
                        chunk.resize(read);
                        body += chunk;
                    }
                    ok = true;
                }
            }
            WinHttpCloseHandle(hReq);
        }
        WinHttpCloseHandle(hConn);
    }
    WinHttpCloseHandle(hSession);
    return ok;
}

// UTF-8 -> UTF-16（Windows API 参数用）
inline std::wstring utf8ToWide(const std::string& s) {
    if (s.empty()) return L"";
    int n = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), nullptr, 0);
    std::wstring out(n, 0);
    MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), &out[0], n);
    return out;
}

// URL 编码（查询参数用）
inline std::string urlEncode(const std::string& s) {
    std::string out;
    static const char* hex = "0123456789ABCDEF";
    for (unsigned char c : s) {
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ||
            c == '-' || c == '_' || c == '.' || c == '~') {
            out += (char)c;
        } else {
            out += '%';
            out += hex[c >> 4];
            out += hex[c & 15];
        }
    }
    return out;
}

} // namespace http
