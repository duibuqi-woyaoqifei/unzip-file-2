#ifndef DECOMPRESSOR_H
#define DECOMPRESSOR_H

#include <string>
#include <functional>

class Decompressor {
public:
    using ProgressCallback = std::function<void(int)>;

    virtual ~Decompressor() = default;
    virtual int extract(int fd, const std::string& destPath, ProgressCallback callback) = 0;
};

#endif // DECOMPRESSOR_H
