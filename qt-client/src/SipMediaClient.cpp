#include "SipMediaClient.h"

#include <QCoreApplication>
#include <QDir>
#include <QFileInfo>
#include <QProcessEnvironment>

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

void SipMediaClient::start(const QJsonObject &mediaConfig, const QString &callId, const QString &mediaType)
{
    stop();

    const QString bin = pjsuaBinary();
    const QString username = value(mediaConfig, "sipUsername");
    const QString password = value(mediaConfig, "sipPassword");
    const QString registrar = value(mediaConfig, "sipRegistrar");
    const QString realm = value(mediaConfig, "sipRealm");
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

    QStringList args;
    args << "--id" << selfUri
         << "--registrar" << registrar
         << "--realm" << (realm.isEmpty() ? QString("*") : realm)
         << "--username" << username
         << "--password" << password
         << "--auto-conf"
         << "--auto-answer=200";

    if (mediaType == "video") {
        args << "--video";
        emit logLine("SIP VIDEO enabled; camera/render success still depends on real device runtime.");
    } else {
        args << "--null-video";
    }

    if (!calleeUri.isEmpty()) {
        args << calleeUri;
    }

    emit logLine("SIP START call=" + callId + " mediaType=" + mediaType + " bin=" + bin);
    emit logLine("SIP ARGS " + args.join(" "));
    process.setWorkingDirectory(QFileInfo(bin).absolutePath());
    process.start(bin, args);
    if (!process.waitForStarted(3000)) {
        emit logLine("SIP START failed: " + process.errorString());
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
