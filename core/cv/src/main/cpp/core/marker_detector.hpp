#pragma once

#include <opencv2/core.hpp>
#include <array>
#include <vector>

namespace smartshape {

struct Marker {
    int id;
    std::array<cv::Point2f, 4> corners;  // TL, TR, BR, BL of the marker's black square, image pixels
};

// Detects SmartShape calibration markers (ArUco DICT_4X4_50) with sub-pixel corners.
std::vector<Marker> detectMarkers(const cv::Mat &gray);

}  // namespace smartshape
