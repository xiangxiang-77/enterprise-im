#include "SipMediaClient.h"

#include <QCoreApplication>
#include <QDir>
#include <QFileInfo>
#include <QTimer>

SipMediaClient::SipMediaClient(QObject *parent)
    : QObject(parent)
{
    connect(&process, &QProcess::readyReadStandardOutput, this, [this]() {
        emit logLine("SIP OUT " + QString::fromLocal8Bit(process.readAllStandardOutput()).trimmed());
    });
    connect(&process, &QProcess::readyReadStandardError, this, [this]() {
        emit logLine("SIP ERR " + QString::fromLocal8Bit(process.readAllStandardError()).trimmed());
    });
    connect(&process, static_cast<void (QProcess::*)(int, QProcess::ExitStatus)>(&QProcess::finished),
            this, [this](int code, QProcess::ExitStatus) {
                emit logLine("SIP EXIT code=" + QString::number(code));
            });
}

SipMediaClient::~SipMediaClient()
{
    stop();
}

void SipMediaClient::start(const QJsonObject &mediaConfig, const QString &callId, const QString &mediaType, bool outbound)
{
    stop();

    const QString bin = pjsuaBinary();
    const QString username = value(mediaConfig, "sipUsername");
    const QString password = value(mediaConfig, "sipPassword");
    const QString registrar = value(mediaConfig, "sipRegistrar");
    const QString selfUri = value(mediaConfig, "selfSipUri");
    const QString calleeUri = value(mediaConfig, "calleeSipUri");

    if (bin.isEmpty()) {
        emit logLine("PJSUA_BIN empty; desktop native audio/video cannot start.");
        return;
    }
    if (username.isEmpty() || password.isEmpty() || registrar.isEmpty() || selfUri.isEmpty()) {
        emit logLine("SIP media config incomplete; native audio/video cannot start.");
        return;
    }

    const QString localPort = QString::fromLocal8Bit(qgetenv("PJSUA_LOCAL_PORT")).trimmed();
    const QString videoCaptureDev = QString::fromLocal8Bit(qgetenv("PJSUA_VIDEO_CAPTURE_DEV")).trimmed();
    const QString audioCaptureDev = QString::fromLocal8Bit(qgetenv("PJSUA_AUDIO_CAPTURE_DEV")).trimmed();
    const QString audioPlaybackDev = QString::fromLocal8Bit(qgetenv("PJSUA_AUDIO_PLAYBACK_DEV")).trimmed();

    QStringList args;
    args << (localPort.isEmpty() ? QString("--local-port=5062") : "--local-port=" + localPort)
         << "--id" << selfUri
         << "--registrar" << registrar
         << "--realm=*"
         << "--username" << username
         << "--password" << password
         << "--auto-conf"
         << "--auto-answer=200";

    if (!audioCaptureDev.isEmpty()) {
        args << "--capture-dev=" + audioCaptureDev;
    }
    if (!audioPlaybackDev.isEmpty()) {
        args << "--playback-dev=" + audioPlaybackDev;
    }

    if (mediaType == "video") {
        const QString captureDev = videoCaptureDev.isEmpty() ? QString("0") : videoCaptureDev;
        args << "--video" << "--vcapture-dev=" + captureDev;
        emit logLine("SIP VIDEO enabled; camera/render success still depends on real device runtime.");
    }

    if (outbound && !calleeUri.isEmpty()) {
        args << calleeUri;
    }

    emit logLine("SIP START call=" + callId + " mediaType=" + mediaType + " outbound=" + QString(outbound ? "true" : "false") + " bin=" + bin);
    emit logLine("SIP ARGS " + args.join(" "));
    process.setWorkingDirectory(QFileInfo(bin).absolutePath());
    process.start(bin, args);
    if (!process.waitForStarted(3000)) {
        emit logLine("SIP START failed: " + process.errorString());
        return;
    }

    if (mediaType == "video") {
        QTimer::singleShot(400, this, [this]() {
            if (process.state() != QProcess::Running) {
                return;
            }
            process.write("vid enable\n");
            const QByteArray captureDev = qgetenv("PJSUA_VIDEO_CAPTURE_DEV").trimmed();
            process.write("vid acc cap ");
            process.write(captureDev.isEmpty() ? "0" : captureDev);
            process.write("\n");
            process.write("vid acc autorx off\n");
            process.write("vid acc autotx on\n");
            process.write("vid codec prio H264 255\n");
            process.write("vid codec prio VP8 254\n");
            process.write("vid win arrange\n");
            emit logLine("SIP VIDEO commands sent: vid enable, configurable capture dev, autorx off, autotx on");
        });
    }
}

void SipMediaClient::stop()
{
    if (process.state() == QProcess::NotRunning) {
        return;
    }
    emit logLine("SIP STOP");
    process.terminate();
    if (!process.waitForFinished(1500)) {
        process.kill();
        process.waitForFinished(1500);
    }
}

QString SipMediaClient::pjsuaBinary() const
{
    const QByteArray configured = qgetenv("PJSUA_BIN");
    if (!configured.trimmed().isEmpty()) {
        return QString::fromLocal8Bit(configured);
    }
    const QString bundled = QDir(QCoreApplication::applicationDirPath()).filePath("pjsua.exe");
    if (QFileInfo::exists(bundled)) {
        return bundled;
    }
    return "pjsua";
}

QString SipMediaClient::value(const QJsonObject &object, const QString &name) const
{
    return object.value(name).toString().trimmed();
}
