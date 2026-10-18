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

// ---- Sticker detection -------------------------------------------------------------------------
// Stickers are sharp rectangles that differ from their surroundings in one of three ways: much
// brighter (plain white labels), densely printed and brighter (logo stickers), or strongly
// coloured. Keyboard keys and other background rectangles are grey, plain and come in rows of
// identical copies, which is how they are told apart. Thresholds were tuned on real photos of
// laptop stickers; see the unit tests in the app for the measurement side.

cv::Mat edgeMapScaled(const cv::Mat &blurred, double kLo, double kHi) {
    const double m = medianIntensity(blurred);
    const double lo = std::max(10.0, kLo * m);
    const double hi = std::min(255.0, std::max(lo + 20.0, kHi * m));
    cv::Mat edges;
    cv::Canny(blurred, edges, lo, hi);
    cv::morphologyEx(edges, edges, cv::MORPH_CLOSE,
                     cv::getStructuringElement(cv::MORPH_RECT, cv::Size(3, 3)), cv::Point(-1, -1), 2);
    return edges;
}

struct Candidate {
    std::vector<cv::Point2f> quad;
    cv::Point2f centre;
    float longSide, shortSide;
    double area;
};

void addQuads(const cv::Mat &edges, std::vector<Candidate> &out) {
    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(edges, contours, cv::RETR_LIST, cv::CHAIN_APPROX_NONE);
    const double imageArea = static_cast<double>(edges.total());
    for (const auto &c : contours) {
        const double a = cv::contourArea(c);
        if (a < 0.0015 * imageArea || a > 0.6 * imageArea) continue;
        std::vector<cv::Point> approx;
        cv::approxPolyDP(c, approx, 0.02 * cv::arcLength(c, true), true);
        if (approx.size() != 4 || !cv::isContourConvex(approx)) continue;
        const auto q = toFloat(approx);
        double maxCos = 0;
        for (int i = 0; i < 4; ++i) maxCos = std::max(maxCos, std::abs(cosAngle(q[(i + 3) % 4], q[i], q[(i + 1) % 4])));
        if (maxCos > 0.3) continue;
        const cv::RotatedRect r = cv::minAreaRect(q);
        out.push_back({q, (q[0] + q[1] + q[2] + q[3]) * 0.25f, std::max(r.size.width, r.size.height),
                       std::min(r.size.width, r.size.height), cv::contourArea(q)});
    }
}

// Similar-sized rectangles in the same row, e.g. neighbouring keys.
int rowSiblings(const Candidate &c, const std::vector<Candidate> &all) {
    std::vector<cv::Point2f> seen;
    for (const auto &o : all) {
        const double d = cv::norm(o.centre - c.centre);
        if (d < 2 || d > 3.5 * c.longSide) continue;
        if (std::abs(o.longSide - c.longSide) > 0.12 * c.longSide || std::abs(o.shortSide - c.shortSide) > 0.12 * c.shortSide) continue;
        if (std::abs(o.centre.y - c.centre.y) > 0.3 * c.shortSide) continue;
        bool dup = false;  // the same key outlined by both edge passes
        for (const auto &p : seen) dup |= cv::norm(o.centre - p) < 0.3 * c.shortSide;
        if (!dup) seen.push_back(o.centre);
    }
    return static_cast<int>(seen.size());
}

std::vector<Shape> detectStickers(const cv::Mat &gray, const cv::Mat &rgb) {
    cv::Mat blurred, fine, sat;
    cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 0);
    cv::Canny(blurred, fine, 40, 120);  // print density inside a candidate
    if (!rgb.empty()) {
        cv::Mat hsv;
        cv::cvtColor(rgb, hsv, cv::COLOR_RGB2HSV);
        cv::extractChannel(hsv, sat, 1);
    }

    // Two edge passes: the usual one, and a sensitive one for low-contrast sticker borders.
    std::vector<Candidate> cands;
    addQuads(edgeMapScaled(blurred, 0.66, 1.33), cands);
    addQuads(edgeMapScaled(blurred, 0.25, 0.5), cands);

    // Thin edges cover a larger share of a small image, so the density bar rises as resolution drops.
    const double densityThreshold = 0.06 * std::sqrt(1600.0 / std::max(gray.cols, gray.rows));
    std::vector<std::pair<double, Shape>> found;
    for (const auto &c : cands) {
        const cv::Rect box = cv::boundingRect(c.quad);
        // A sticker cut off by the frame edge can't be measured.
        if (box.x <= 2 || box.y <= 2 || box.br().x >= gray.cols - 2 || box.br().y >= gray.rows - 2) continue;

        const int kInner = std::max(3, static_cast<int>(c.shortSide * 0.12)) | 1;
        const int kRing = std::max(5, static_cast<int>(c.shortSide * 0.25)) | 1;
        const cv::Rect roi = (box + cv::Size(2 * kRing, 2 * kRing) - cv::Point(kRing, kRing)) & cv::Rect(0, 0, gray.cols, gray.rows);
        cv::Mat mask = cv::Mat::zeros(roi.size(), CV_8U);
        std::vector<cv::Point> local;
        for (const auto &p : c.quad) local.emplace_back(cvRound(p.x) - roi.x, cvRound(p.y) - roi.y);
        cv::fillConvexPoly(mask, local, 255);
        cv::Mat inner, ring, grown;
        cv::erode(mask, inner, cv::getStructuringElement(cv::MORPH_RECT, cv::Size(kInner, kInner)));
        cv::dilate(mask, ring, cv::getStructuringElement(cv::MORPH_RECT, cv::Size(kRing, kRing)));
        cv::dilate(mask, grown, cv::getStructuringElement(cv::MORPH_RECT, cv::Size(3, 3)));
        ring &= ~grown;
        if (cv::countNonZero(inner) == 0 || cv::countNonZero(ring) == 0) continue;

        const cv::Mat g = blurred(roi);
        const double contrast = cv::mean(g, mask)[0] - cv::mean(g, ring)[0];
        const double density = cv::mean(fine(roi), inner)[0] / 255.0;
        double sat90 = 0;
        if (!sat.empty()) {
            int hist[256] = {0}, n = 0;
            const cv::Mat s = sat(roi);
            for (int y = 0; y < s.rows; ++y)
                for (int x = 0; x < s.cols; ++x)
                    if (inner.at<uchar>(y, x)) { hist[s.at<uchar>(y, x)]++; ++n; }
            for (int i = 255, acc = 0; i >= 0; --i) {
                acc += hist[i];
                if (acc >= n / 10) { sat90 = i; break; }
            }
        }

        bool sticker;
        if (sat90 > 120) sticker = true;                                   // coloured
        else if (density > densityThreshold && contrast > 25) sticker = true;  // printed
        else if (contrast > 80) sticker = rowSiblings(c, cands) < 2;           // plain bright, not a row of keys
        else sticker = false;
        if (!sticker) continue;

        Shape shape{};
        shape.points = c.quad;
        shape.area = c.area;
        shape.type = c.shortSide / c.longSide > 0.95 ? ShapeType::Square : ShapeType::Rectangle;
        shape.confidence = static_cast<float>(std::min(1.0, 0.6 + std::max({contrast / 200.0, density, sat90 / 400.0})));
        found.emplace_back(c.area, shape);
    }

    // Largest first; anything centred inside a kept sticker is its inner outline or a printed logo.
    std::sort(found.begin(), found.end(), [](const auto &a, const auto &b) { return a.first > b.first; });
    std::vector<Shape> out;
    for (auto &f : found) {
        const auto &q = f.second.points;
        const cv::Point2f c = (q[0] + q[1] + q[2] + q[3]) * 0.25f;
        bool nested = false;
        for (const auto &k : out) nested |= cv::pointPolygonTest(k.points, c, false) >= 0;
        if (!nested) out.push_back(std::move(f.second));
    }
    return out;
}

bool isVertexShape(ShapeType t) {
    return t == ShapeType::Rectangle || t == ShapeType::Square || t == ShapeType::Triangle || t == ShapeType::Polygon;
}

}  // namespace

std::vector<Shape> detectShapes(const cv::Mat &gray, const DetectorParams &params, const cv::Mat &rgb) {
    std::vector<Shape> shapes;
    if (gray.empty() || gray.type() != CV_8UC1) return shapes;

    const int longest = std::max(gray.cols, gray.rows);
    const double scale = longest > params.maxDim ? static_cast<double>(params.maxDim) / longest : 1.0;
    cv::Mat work;
    if (scale < 1.0) cv::resize(gray, work, cv::Size(), scale, scale, cv::INTER_AREA);
    else work = gray;

    if (params.stickersOnly) {
        cv::Mat workRgb;
        if (!rgb.empty()) {
            if (scale < 1.0) cv::resize(rgb, workRgb, work.size(), 0, 0, cv::INTER_AREA);
            else workRgb = rgb;
        }
        shapes = detectStickers(work, workRgb);
    } else {
        std::vector<std::vector<cv::Point>> contours;
        cv::findContours(edgeMap(work), contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_NONE);

        const double imageArea = static_cast<double>(work.total());
        for (const auto &c : contours) {
            const double a = cv::contourArea(c);
            if (a < params.minAreaFraction * imageArea || a > params.maxAreaFraction * imageArea) continue;
            Shape s{};
            if (classify(c, s)) shapes.push_back(std::move(s));
        }
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
