#ifndef SIPMEDIACLIENT_H
#define SIPMEDIACLIENT_H

#include <QObject>
#include <QJsonObject>
#include <QProcess>

class SipMediaClient : public QObject
{
    Q_OBJECT

public:
    explicit SipMediaClient(QObject *parent = nullptr);

    void start(const QJsonObject &mediaConfig, const QString &callId, const QString &mediaType);
    void stop();

signals:
    void logLine(const QString &line);

private:
    QProcess process;

    QString pjsuaBinary() const;
    QString value(const QJsonObject &object, const QString &name) const;
};

#endif
