#include "core/shape_detector.hpp"

#include <opencv2/imgproc.hpp>
#include <algorithm>
#include <cmath>

namespace smartshape {
namespace {

constexpr double kPi = 3.14159265358979323846;
constexpr int kEllipseSamples = 72;

double medianIntensity(const cv::Mat &gray) {
    int hist[256] = {0};
    for (int y = 0; y < gray.rows; ++y) {
        const uchar *row = gray.ptr<uchar>(y);
        for (int x = 0; x < gray.cols; ++x) hist[row[x]]++;
    }
    const int half = static_cast<int>(gray.total() / 2);
    int acc = 0;
    for (int i = 0; i < 256; ++i) {
        acc += hist[i];
        if (acc >= half) return i;
    }
    return 127;
}

// Closed edge map: Canny with thresholds derived from the image median, then a small closing.
cv::Mat edgeMap(const cv::Mat &gray) {
    cv::Mat blurred, edges;
    cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 0);
    const double m = medianIntensity(blurred);
    const double lo = std::max(10.0, 0.66 * m);
    const double hi = std::min(255.0, std::max(lo + 20.0, 1.33 * m));
    cv::Canny(blurred, edges, lo, hi);
    cv::morphologyEx(edges, edges, cv::MORPH_CLOSE,
                     cv::getStructuringElement(cv::MORPH_RECT, cv::Size(3, 3)), cv::Point(-1, -1), 2);
    return edges;
}

double cosAngle(const cv::Point2f &a, const cv::Point2f &b, const cv::Point2f &c) {
    // Angle at b.
    const cv::Point2f u = a - b, v = c - b;
    const double n = std::sqrt(u.dot(u) * v.dot(v));
    return n < 1e-9 ? 1.0 : u.dot(v) / n;
}

std::vector<cv::Point2f> toFloat(const std::vector<cv::Point> &pts) {
    std::vector<cv::Point2f> out;
    out.reserve(pts.size());
    for (const auto &p : pts) out.emplace_back(static_cast<float>(p.x), static_cast<float>(p.y));
    return out;
}

std::vector<cv::Point2f> sampleEllipse(const cv::RotatedRect &e) {
    std::vector<cv::Point2f> out;
    out.reserve(kEllipseSamples);
    const double a = e.size.width / 2.0, b = e.size.height / 2.0;
    const double t = e.angle * kPi / 180.0;
    for (int i = 0; i < kEllipseSamples; ++i) {
        const double s = 2 * kPi * i / kEllipseSamples;
        const double x = a * std::cos(s), y = b * std::sin(s);
        out.emplace_back(static_cast<float>(e.center.x + x * std::cos(t) - y * std::sin(t)),
                         static_cast<float>(e.center.y + x * std::sin(t) + y * std::cos(t)));
    }
    return out;
}

// Mean normalised radial error of contour points against a fitted ellipse (0 = perfect).
double ellipseResidual(const std::vector<cv::Point> &contour, const cv::RotatedRect &e) {
    const double a = e.size.width / 2.0, b = e.size.height / 2.0;
    if (a < 1 || b < 1) return 1.0;
    const double t = -e.angle * kPi / 180.0;
    double err = 0;
    for (const auto &p : contour) {
        const double dx = p.x - e.center.x, dy = p.y - e.center.y;
        const double x = dx * std::cos(t) - dy * std::sin(t);
        const double y = dx * std::sin(t) + dy * std::cos(t);
        err += std::abs(std::sqrt((x * x) / (a * a) + (y * y) / (b * b)) - 1.0);
    }
    return err / static_cast<double>(contour.size());
}

bool classify(const std::vector<cv::Point> &contour, Shape &out) {
    const double area = cv::contourArea(contour);
    const double peri = cv::arcLength(contour, true);
    if (peri <= 0) return false;

    std::vector<cv::Point> approx;
    cv::approxPolyDP(contour, approx, 0.02 * peri, true);
    const bool convex = cv::isContourConvex(approx);
    const double approxFit = 1.0 - std::min(1.0, std::abs(cv::contourArea(approx) - area) / area);
    out.area = area;

    if (convex && approx.size() == 3) {
        out.type = ShapeType::Triangle;
        out.points = toFloat(approx);
        out.confidence = static_cast<float>(approxFit);
        return true;
    }

    if (convex && approx.size() == 4) {
        const auto q = toFloat(approx);
        double maxCos = 0;
        for (int i = 0; i < 4; ++i) maxCos = std::max(maxCos, std::abs(cosAngle(q[(i + 3) % 4], q[i], q[(i + 1) % 4])));
        out.points = q;
        if (maxCos < 0.3) {
            const double s1 = (cv::norm(q[0] - q[1]) + cv::norm(q[2] - q[3])) / 2;
            const double s2 = (cv::norm(q[1] - q[2]) + cv::norm(q[3] - q[0])) / 2;
            const double ratio = std::min(s1, s2) / std::max(s1, s2);
            out.type = ratio > 0.95 ? ShapeType::Square : ShapeType::Rectangle;
            out.confidence = static_cast<float>(approxFit * (1.0 - maxCos));
        } else {
            out.type = ShapeType::Polygon;
            out.confidence = static_cast<float>(approxFit * 0.8);
        }
        return true;
    }

    if (contour.size() >= 5) {
        const cv::RotatedRect e = cv::fitEllipse(contour);
        const double residual = ellipseResidual(contour, e);
        const double ellipseArea = kPi * e.size.width * e.size.height / 4.0;
        const double areaFit = 1.0 - std::min(1.0, std::abs(ellipseArea - area) / area);
        if (approx.size() >= 5 && residual < 0.03 && areaFit > 0.95) {
            const double axisRatio = std::min(e.size.width, e.size.height) / std::max(e.size.width, e.size.height);
            out.type = axisRatio > 0.92 ? ShapeType::Circle : ShapeType::Ellipse;
            out.points = sampleEllipse(e);
            out.confidence = static_cast<float>(std::max(0.0, 1.0 - residual * 10) * areaFit);
            return true;
        }
    }

    if (convex && approx.size() >= 5 && approx.size() <= 10) {
        out.type = ShapeType::Polygon;
        out.points = toFloat(approx);
        out.confidence = static_cast<float>(approxFit * 0.9);
        return true;
    }

    std::vector<cv::Point> fine;
    cv::approxPolyDP(contour, fine, 0.004 * peri, true);
    out.type = ShapeType::Irregular;
    out.points = toFloat(fine);
    out.confidence = 0.5f;
    return true;
}

bool isVertexShape(ShapeType t) {
    return t == ShapeType::Rectangle || t == ShapeType::Square || t == ShapeType::Triangle || t == ShapeType::Polygon;
}

}  // namespace

std::vector<Shape> detectShapes(const cv::Mat &gray, const DetectorParams &params) {
    std::vector<Shape> shapes;
    if (gray.empty() || gray.type() != CV_8UC1) return shapes;

    const int longest = std::max(gray.cols, gray.rows);
    const double scale = longest > params.maxDim ? static_cast<double>(params.maxDim) / longest : 1.0;
    cv::Mat work;
    if (scale < 1.0) cv::resize(gray, work, cv::Size(), scale, scale, cv::INTER_AREA);
    else work = gray;

    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(edgeMap(work), contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_NONE);

    const double imageArea = static_cast<double>(work.total());
    for (const auto &c : contours) {
        const double a = cv::contourArea(c);
        if (a < params.minAreaFraction * imageArea || a > params.maxAreaFraction * imageArea) continue;
        Shape s{};
        if (classify(c, s)) shapes.push_back(std::move(s));
    }

    std::sort(shapes.begin(), shapes.end(), [](const Shape &a, const Shape &b) { return a.area > b.area; });
    if (static_cast<int>(shapes.size()) > params.maxShapes) shapes.resize(params.maxShapes);

    const float inv = static_cast<float>(1.0 / scale);
    for (auto &s : shapes) {
        for (auto &p : s.points) p *= inv;
        s.area /= scale * scale;
        if (params.refineCorners && isVertexShape(s.type)) {
            const int win = std::max(3, static_cast<int>(std::round(3 * inv)));
            cv::cornerSubPix(gray, s.points, cv::Size(win, win), cv::Size(-1, -1),
                             cv::TermCriteria(cv::TermCriteria::EPS + cv::TermCriteria::COUNT, 30, 0.01));
        }
    }
    return shapes;
}

}  // namespace smartshape
