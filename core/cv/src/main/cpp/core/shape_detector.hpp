#pragma once

#include <opencv2/core.hpp>
#include <vector>

namespace smartshape {

// Ordinals must match com.nevonai.smartshape.model.ShapeType.
enum class ShapeType : int {
    Rectangle = 0,
    Square = 1,
    Circle = 2,
    Triangle = 3,
    Ellipse = 4,
    Polygon = 5,
    Irregular = 6,
};

struct Shape {
    ShapeType type;
    float confidence;               // 0..1, how well the outline fits the classified model
    std::vector<cv::Point2f> points;  // vertices (polygons) or sampled outline (curves), input-image pixels
    double area;                    // contour area in input-image pixels
};

struct DetectorParams {
    int maxDim = 1280;              // processing resolution (longest side)
    double minAreaFraction = 0.002; // ignore blobs smaller than this fraction of the image
    double maxAreaFraction = 0.90;  // ignore the frame border / whole background
    int maxShapes = 8;
    bool refineCorners = true;      // sub-pixel corner refinement at full resolution (captures only)
    bool stickersOnly = true;       // keep only sticker-like rectangles (see detectStickers)
};

// Detects and classifies the outer outlines of objects in a grayscale image. [rgb] is an optional
// 8-bit RGB copy of the same image; when given, colour helps recognise coloured stickers.
std::vector<Shape> detectShapes(const cv::Mat &gray, const DetectorParams &params, const cv::Mat &rgb = cv::Mat());

}  // namespace smartshape
