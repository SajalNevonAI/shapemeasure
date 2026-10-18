cmake_minimum_required(VERSION 3.22.1)
project(smartshape_cv LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# OpenCV comes from the org.opencv:opencv AAR via prefab on Android. For the desktop accuracy
# harness, point OpenCV_DIR at a local OpenCV build instead.
find_package(OpenCV REQUIRED CONFIG)

# Platform-independent vision core (no JNI): reusable from a desktop test harness or iOS later.
add_library(smartshape_core STATIC
        core/shape_detector.cpp
        core/marker_detector.cpp
        core/image_quality.cpp)
target_include_directories(smartshape_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

if (ANDROID)
    target_link_libraries(smartshape_core PUBLIC OpenCV::opencv_java4)

    add_library(smartshape_cv SHARED jni_bridge.cpp)
    target_link_libraries(smartshape_cv PRIVATE smartshape_core jnigraphics log)
else ()
    target_link_libraries(smartshape_core PUBLIC ${OpenCV_LIBS})
endif ()
