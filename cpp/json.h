// 极简 JSON 解析器（只读场景够用）：解析 object/array/string/number/bool/null
#pragma once
#include <string>
#include <vector>
#include <map>
#include <memory>
#include <cstdlib>
#include <cstring>

namespace json {

struct Value;
using Ptr = std::shared_ptr<Value>;

struct Value {
    enum Type { Null, Bool, Number, String, Array, Object } type = Null;
    bool b = false;
    double num = 0;
    std::string str;
    std::vector<Ptr> arr;
    std::vector<std::pair<std::string, Ptr>> obj;

    bool isNull() const { return type == Null; }
    bool isString() const { return type == String; }
    bool isArray() const { return type == Array; }
    bool isObject() const { return type == Object; }

    std::string asString() const {
        if (type == String) return str;
        if (type == Number) {
            char buf[64];
            if (num == (long long)num)
                snprintf(buf, sizeof(buf), "%lld", (long long)num);
            else
                snprintf(buf, sizeof(buf), "%.2f", num);
            return buf;
        }
        if (type == Bool) return b ? "true" : "false";
        return "";
    }
    // 按 key 取对象成员；不存在返回 nullptr
    const Ptr get(const std::string& key) const {
        if (type != Object) return nullptr;
        for (auto& kv : obj) if (kv.first == key) return kv.second;
        return nullptr;
    }
    // 取字符串成员，缺省返回 def
    std::string getStr(const std::string& key, const std::string& def = "") const {
        auto p = get(key);
        return (p && p->isString()) ? p->str : def;
    }
    // 取数字成员（数字或数字字符串）
    long long getInt(const std::string& key, long long def = 0) const {
        auto p = get(key);
        if (!p) return def;
        if (p->type == Number) return (long long)p->num;
        if (p->type == String) return atoll(p->str.c_str());
        return def;
    }
    double getDouble(const std::string& key, double def = 0) const {
        auto p = get(key);
        if (!p) return def;
        if (p->type == Number) return p->num;
        if (p->type == String) return atof(p->str.c_str());
        return def;
    }
};

class Parser {
public:
    explicit Parser(const std::string& text) : s(text), pos(0) {}

    Ptr parse() {
        skipWs();
        auto v = parseValue();
        return v ? v : std::make_shared<Value>();
    }

private:
    const std::string& s;
    size_t pos;

    void skipWs() { while (pos < s.size() && (s[pos] == ' ' || s[pos] == '\t' || s[pos] == '\n' || s[pos] == '\r')) pos++; }
    bool eof() const { return pos >= s.size(); }
    char peek() const { return eof() ? '\0' : s[pos]; }

    Ptr parseValue() {
        char c = peek();
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't' || c == 'f') return parseBool();
        if (c == 'n') return parseNull();
        if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
        return nullptr;
    }

    Ptr parseObject() {
        auto v = std::make_shared<Value>();
        v->type = Value::Object;
        pos++; // {
        skipWs();
        if (peek() == '}') { pos++; return v; }
        while (!eof()) {
            skipWs();
            if (peek() != '"') break;
            auto key = parseString();
            if (!key) break;
            skipWs();
            if (peek() != ':') break;
            pos++;
            skipWs();
            auto val = parseValue();
            if (!val) break;
            v->obj.emplace_back(key->str, val);
            skipWs();
            char c = peek();
            if (c == ',') { pos++; continue; }
            if (c == '}') { pos++; break; }
            break;
        }
        return v;
    }

    Ptr parseArray() {
        auto v = std::make_shared<Value>();
        v->type = Value::Array;
        pos++; // [
        skipWs();
        if (peek() == ']') { pos++; return v; }
        while (!eof()) {
            skipWs();
            auto val = parseValue();
            if (!val) break;
            v->arr.push_back(val);
            skipWs();
            char c = peek();
            if (c == ',') { pos++; continue; }
            if (c == ']') { pos++; break; }
            break;
        }
        return v;
    }

    Ptr parseString() {
        if (peek() != '"') return nullptr;
        pos++;
        std::string out;
        while (!eof()) {
            char c = s[pos++];
            if (c == '"') {
                auto v = std::make_shared<Value>();
                v->type = Value::String;
                v->str = out;
                return v;
            }
            if (c == '\\' && pos < s.size()) {
                char e = s[pos++];
                switch (e) {
                    case '"': out += '"'; break;
                    case '\\': out += '\\'; break;
                    case '/': out += '/'; break;
                    case 'b': out += '\b'; break;
                    case 'f': out += '\f'; break;
                    case 'n': out += '\n'; break;
                    case 'r': out += '\r'; break;
                    case 't': out += '\t'; break;
                    case 'u': {
                        // 解析 \uXXXX（转成 UTF-8），简单处理常用 BMP 字符
                        if (pos + 4 <= s.size()) {
                            unsigned cp = (unsigned)strtoul(s.substr(pos, 4).c_str(), nullptr, 16);
                            pos += 4;
                            if (cp < 0x80) out += (char)cp;
                            else if (cp < 0x800) {
                                out += (char)(0xC0 | (cp >> 6));
                                out += (char)(0x80 | (cp & 0x3F));
                            } else {
                                out += (char)(0xE0 | (cp >> 12));
                                out += (char)(0x80 | ((cp >> 6) & 0x3F));
                                out += (char)(0x80 | (cp & 0x3F));
                            }
                        }
                        break;
                    }
                    default: out += e; break;
                }
            } else {
                out += c;
            }
        }
        return nullptr;
    }

    Ptr parseNumber() {
        size_t start = pos;
        if (peek() == '-') pos++;
        while (!eof() && ((peek() >= '0' && peek() <= '9') || peek() == '.' || peek() == 'e' || peek() == 'E' || peek() == '+' || peek() == '-')) pos++;
        auto v = std::make_shared<Value>();
        v->type = Value::Number;
        v->num = atof(s.substr(start, pos - start).c_str());
        return v;
    }

    Ptr parseBool() {
        if (s.compare(pos, 4, "true") == 0) { pos += 4; auto v = std::make_shared<Value>(); v->type = Value::Bool; v->b = true; return v; }
        if (s.compare(pos, 5, "false") == 0) { pos += 5; auto v = std::make_shared<Value>(); v->type = Value::Bool; v->b = false; return v; }
        return nullptr;
    }

    Ptr parseNull() {
        if (s.compare(pos, 4, "null") == 0) { pos += 4; return std::make_shared<Value>(); }
        return nullptr;
    }
};

inline Ptr parse(const std::string& text) { return Parser(text).parse(); }

} // namespace json
