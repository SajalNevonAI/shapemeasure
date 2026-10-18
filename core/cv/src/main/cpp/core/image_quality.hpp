#pragma once

#include <opencv2/core.hpp>

namespace smartshape {

struct ImageQuality {
    double sharpness;   // variance of the Laplacian on a 640px downscale; < ~60 is usually blurred
    double brightness;  // mean intensity 0..255
};

ImageQuality measureQuality(const cv::Mat &gray);

}  // namespace smartshape
