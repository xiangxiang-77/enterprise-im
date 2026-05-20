#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QByteArray>
#include <QJsonObject>
#include <QMainWindow>
#include <QNetworkAccessManager>
#include <QTcpSocket>

class SipMediaClient;
class QLineEdit;
class QLabel;
class QListWidget;
class QPushButton;
class QTextEdit;
class QWidget;

#ifdef ENTERPRISE_IM_HAS_MULTIMEDIA
class QCamera;
class QCameraViewfinder;
#endif

class MainWindow : public QMainWindow
{
    Q_OBJECT

public:
    explicit MainWindow(QWidget *parent = nullptr);

private:
    QLineEdit *hostEdit;
    QLineEdit *httpPortEdit;
    QLineEdit *tcpPortEdit;
    QLineEdit *userIdEdit;
    QLineEdit *tokenEdit;
    QLineEdit *peerIdEdit;
    QLineEdit *conversationIdEdit;
    QLineEdit *messageEdit;
    QLabel *statusLabel;
    QLabel *callStatusLabel;
    QLabel *actionStatusLabel;
    QListWidget *conversationList;
    QListWidget *messageList;
    QTextEdit *eventView;
    QPushButton *connectButton;
    QPushButton *authButton;
    QPushButton *pingButton;
    QPushButton *sendButton;
    QPushButton *readinessButton;
    QPushButton *audioButton;
    QPushButton *videoButton;
    QPushButton *answerButton;
    QPushButton *rejectButton;
    QPushButton *hangupButton;
    QPushButton *historyButton;
    QPushButton *debugToggleButton;
    QWidget *callPanel;
    QWidget *composerPanel;
    QWidget *callScreen;
    QLabel *callScreenTitle;
    QLabel *callScreenState;
    QLabel *callScreenPeer;
    QLabel *callScreenAvatar;
    QLabel *callScreenHint;
    QPushButton *callScreenAnswerButton;
    QPushButton *callScreenRejectButton;
    QPushButton *callScreenHangupButton;
#ifdef ENTERPRISE_IM_HAS_MULTIMEDIA
    QCamera *camera;
    QCameraViewfinder *cameraViewfinder;
#endif

    QTcpSocket socket;
    QNetworkAccessManager http;
    QByteArray pending;
    QString activeCallId;
    QString activeMediaType;
    QString activeCallStatus;
    bool activeCallIncoming;
    QString nativeStartedCallId;
    SipMediaClient *sipMediaClient;
    bool debugVisible;

    void connectToServer();
    void sendAuth();
    void sendPing();
    void sendText();
    void checkCallReadiness();
    void startAudioCall();
    void startVideoCall();
    void answerCall();
    void rejectCall();
    void hangupCall();
    void loadCallHistory();
    void readSocket();
    void handleSocketLine(const QByteArray &line);
    void updateConnectionState();
    void appendLog(const QString &line);
    void appendMessage(const QString &sender, const QString &content);
    void updateCallStatus(const QString &status);
    void refreshCallControls();
    void showCallScreen();
    void hideCallScreen();
    void updateCallScreen();
    void startLocalCameraPreview();
    void stopLocalCameraPreview();
    void setActionStatus(const QString &text, bool error = false);
    QString callStatusText(const QString &status) const;
    QString mediaTypeText(const QString &mediaType) const;
    void initLocalStore();
    void saveLocalMessage(const QString &sender, const QString &content);
    void saveLocalCall(const QString &callId, const QString &mediaType, const QString &status);
    void sendFrame(const QString &type, const QString &to, const QString &conversationId, const QJsonObject &payload);
    void startCall(const QString &mediaType);
    void transitionCall(const QString &action);
    void requestMediaConfig();
    void apiGet(const QString &path);
    void apiPost(const QString &path, const QJsonObject &body);
    void sendApiRequest(const QString &method, const QString &path, const QJsonObject &body);
    void handleApiResponse(const QString &label, const QByteArray &body);
    QString baseUrl() const;
};

#endif
