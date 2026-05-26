QT += core gui network widgets sql

qtHaveModule(multimediawidgets) {
    QT += multimedia multimediawidgets
    DEFINES += ENTERPRISE_IM_HAS_MULTIMEDIA
} else {
    message("Qt MultimediaWidgets not available; in-app camera preview will be disabled.")
}

qtHaveModule(webenginewidgets) {
    QT += webenginewidgets
    DEFINES += ENTERPRISE_IM_HAS_WEBENGINE
} else {
    message("Qt WebEngineWidgets not available in this kit; VS2017 Qt WebEngine build will enable it.")
}

CONFIG += c++11

win32-msvc* {
    QMAKE_CXXFLAGS += /utf-8
    QMAKE_CFLAGS += /utf-8
}

win32 {
    LIBS += -lwinmm -lole32
}

TARGET = EnterpriseIMQtClient
TEMPLATE = app

SOURCES += \
    src/main.cpp \
    src/MainWindow.cpp \
    src/SipMediaClient.cpp

HEADERS += \
    src/MainWindow.h \
    src/SipMediaClient.h
