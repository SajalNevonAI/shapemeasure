#include "core/image_quality.hpp"

#include <opencv2/imgproc.hpp>
#include <algorithm>

namespace smartshape {

ImageQuality measureQuality(const cv::Mat &gray) {
    if (gray.empty()) return {0, 0};
    const int longest = std::max(gray.cols, gray.rows);
    const double scale = longest > 640 ? 640.0 / longest : 1.0;
    cv::Mat small, lap;
    if (scale < 1.0) cv::resize(gray, small, cv::Size(), scale, scale, cv::INTER_AREA);
    else small = gray;
    cv::Laplacian(small, lap, CV_64F);
    cv::Scalar mean, stddev;
    cv::meanStdDev(lap, mean, stddev);
    return {stddev[0] * stddev[0], cv::mean(small)[0]};
}

}  // namespace smartshape
