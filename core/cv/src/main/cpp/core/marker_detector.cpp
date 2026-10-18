#include "core/marker_detector.hpp"

#include <opencv2/objdetect/aruco_detector.hpp>

namespace smartshape {

std::vector<Marker> detectMarkers(const cv::Mat &gray) {
    static const cv::aruco::Dictionary dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_4X4_50);
    cv::aruco::DetectorParameters params;
    params.cornerRefinementMethod = cv::aruco::CORNER_REFINE_SUBPIX;
    const cv::aruco::ArucoDetector detector(dictionary, params);

    std::vector<std::vector<cv::Point2f>> corners;
    std::vector<int> ids;
    detector.detectMarkers(gray, corners, ids);

    std::vector<Marker> out;
    out.reserve(ids.size());
    for (size_t i = 0; i < ids.size(); ++i) {
        Marker m{ids[i], {}};
        for (int k = 0; k < 4; ++k) m.corners[k] = corners[i][k];
        out.push_back(m);
    }
    return out;
}

}  // namespace smartshape
