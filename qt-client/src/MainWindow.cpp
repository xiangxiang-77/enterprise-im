#include "MainWindow.h"
#include "SipMediaClient.h"

#include <QDateTime>
#include <QFormLayout>
#include <QGridLayout>
#include <QHBoxLayout>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonValue>
#include <QLabel>
#include <QLineEdit>
#include <QListWidget>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QPushButton>
#include <QSizePolicy>
#include <QSqlDatabase>
#include <QSqlError>
#include <QSqlQuery>
#include <QSplitter>
#include <QTextEdit>
#include <QUrl>
#include <QUrlQuery>
#include <QUuid>
#include <QVBoxLayout>
#include <QWidget>

#ifdef ENTERPRISE_IM_HAS_MULTIMEDIA
#include <QCamera>
#include <QCameraInfo>
#include <QCameraViewfinder>
#endif

#ifdef ENTERPRISE_IM_HAS_WEBENGINE
#include <QWebEngineView>
#endif

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent),
      sipMediaClient(nullptr),
      activeCallIncoming(false),
      debugVisible(false)
#ifdef ENTERPRISE_IM_HAS_MULTIMEDIA
      ,
      camera(nullptr),
      cameraViewfinder(nullptr)
#endif
{
    setWindowTitle("企业即时通讯桌面端");
    resize(1360, 860);

    hostEdit = new QLineEdit("127.0.0.1");
    httpPortEdit = new QLineEdit("18080");
    tcpPortEdit = new QLineEdit("19090");
    userIdEdit = new QLineEdit("u_qt");
    tokenEdit = new QLineEdit("demo-token-u_qt");
    peerIdEdit = new QLineEdit("u_flutter");
    conversationIdEdit = new QLineEdit("c_qt_flutter");
    messageEdit = new QLineEdit("你好，这是一条来自桌面端的消息");
    statusLabel = new QLabel("离线 - TCP 未连接");
    statusLabel->setObjectName("statusPill");
    callStatusLabel = new QLabel("通话：未开始");
    actionStatusLabel = new QLabel("准备就绪。连接认证后即可发送消息或发起语音。");
    actionStatusLabel->setWordWrap(true);
    actionStatusLabel->setObjectName("notice");
    conversationList = new QListWidget;
    messageList = new QListWidget;
    messageList->setObjectName("messageList");
    messageList->setMinimumWidth(0);
    messageList->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    messageList->setWordWrap(true);
    messageList->setSpacing(10);
    messageList->setFixedHeight(170);
    eventView = new QTextEdit;
    eventView->setReadOnly(true);
    eventView->setVisible(debugVisible);
    sipMediaClient = new SipMediaClient(this);
    initLocalStore();

    conversationList->addItem("Flutter 手机端  u_flutter");
    conversationList->addItem("Web 客户端  u_web");
    conversationList->addItem("企业群聊  c_team");
    conversationList->setCurrentRow(0);

    messageList->addItem("系统  企业 IM 桌面端已就绪");
    messageList->addItem("系统  当前会话：Flutter 手机端");

    connectButton = new QPushButton("连接");
    auto *settingsButton = new QPushButton("连接设置");
    authButton = new QPushButton("登录认证");
    pingButton = new QPushButton("心跳");
    sendButton = new QPushButton("发送");
    readinessButton = new QPushButton("检测通话");
    audioButton = new QPushButton("语音");
    videoButton = new QPushButton("视频");
    answerButton = new QPushButton("接听");
    rejectButton = new QPushButton("拒绝");
    hangupButton = new QPushButton("挂断");
    historyButton = new QPushButton("通话记录");
    debugToggleButton = new QPushButton("显示调试日志");
    auto *topAudioButton = new QPushButton("语音");
    auto *topVideoButton = new QPushButton("视频");
    auto *topHangupButton = new QPushButton("挂断");

    sendButton->setObjectName("primaryButton");
    audioButton->setObjectName("primaryButton");
    videoButton->setObjectName("primaryButton");
    topAudioButton->setObjectName("primaryButton");
    topVideoButton->setObjectName("primaryButton");
    hangupButton->setObjectName("dangerButton");
    topHangupButton->setObjectName("dangerButton");
    sendButton->setMinimumWidth(92);
    topAudioButton->setMinimumWidth(76);
    topVideoButton->setMinimumWidth(76);
    topHangupButton->setMinimumWidth(76);
    readinessButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    audioButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    videoButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    answerButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    rejectButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    hangupButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    historyButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    readinessButton->setMaximumWidth(360);
    audioButton->setMaximumWidth(360);
    videoButton->setMaximumWidth(360);
    answerButton->setMaximumWidth(360);
    rejectButton->setMaximumWidth(360);
    hangupButton->setMaximumWidth(360);
    historyButton->setMaximumWidth(740);

    auto *form = new QFormLayout;
    form->setLabelAlignment(Qt::AlignLeft);
    form->setFormAlignment(Qt::AlignTop);
    form->setVerticalSpacing(10);
    form->addRow("服务器", hostEdit);
    form->addRow("HTTP", httpPortEdit);
    form->addRow("TCP", tcpPortEdit);
    form->addRow("当前用户", userIdEdit);
    form->addRow("Token", tokenEdit);
    form->addRow("对方用户", peerIdEdit);
    form->addRow("会话 ID", conversationIdEdit);

    auto *connectionButtons = new QHBoxLayout;
    connectionButtons->setSpacing(8);
    connectionButtons->addWidget(connectButton);
    connectionButtons->addWidget(authButton);
    connectionButtons->addWidget(pingButton);

    auto *callButtons = new QGridLayout;
    callButtons->setSpacing(10);
    callButtons->addWidget(readinessButton, 0, 0);
    callButtons->addWidget(audioButton, 0, 1);
    callButtons->addWidget(videoButton, 1, 0);
    callButtons->addWidget(answerButton, 1, 1);
    callButtons->addWidget(rejectButton, 2, 0);
    callButtons->addWidget(hangupButton, 2, 1);
    callButtons->addWidget(historyButton, 3, 0, 1, 2);

    auto *configPanel = new QWidget;
    configPanel->setLayout(form);
    configPanel->setVisible(false);

    auto *leftLayout = new QVBoxLayout;
    leftLayout->setContentsMargins(22, 22, 18, 22);
    leftLayout->setSpacing(12);
    auto *brand = new QLabel("企业 IM");
    brand->setObjectName("brand");
    leftLayout->addWidget(brand);
    leftLayout->addWidget(statusLabel);
    leftLayout->addWidget(settingsButton);
    auto *configTitle = new QLabel("连接配置");
    configTitle->setObjectName("sideTitle");
    configTitle->setVisible(false);
    leftLayout->addWidget(configTitle);
    leftLayout->addWidget(configPanel);
    leftLayout->addLayout(connectionButtons);
    auto *convTitle = new QLabel("会话");
    convTitle->setObjectName("sideTitle");
    leftLayout->addWidget(convTitle);
    leftLayout->addWidget(conversationList, 1);

    auto *leftPanel = new QWidget;
    leftPanel->setObjectName("leftPanel");
    leftPanel->setMinimumWidth(330);
    leftPanel->setMaximumWidth(380);
    leftPanel->setLayout(leftLayout);

    auto *chatHeader = new QHBoxLayout;
    chatHeader->setContentsMargins(0, 0, 0, 6);
    auto *title = new QLabel("Flutter 手机端");
    title->setObjectName("sectionTitle");
    title->setMinimumWidth(0);
    chatHeader->addWidget(title);
    chatHeader->addStretch();
    chatHeader->addWidget(topAudioButton);
    chatHeader->addWidget(topVideoButton);
    chatHeader->addWidget(topHangupButton);
    chatHeader->addWidget(callStatusLabel);

    auto *composer = new QHBoxLayout;
    composer->setSpacing(10);
    messageEdit->setMinimumHeight(42);
    messageEdit->setMinimumWidth(0);
    composer->addWidget(messageEdit, 1);
    composer->addWidget(sendButton);
    composerPanel = new QWidget;
    composerPanel->setLayout(composer);

    callScreen = new QWidget;
    callScreen->setObjectName("callScreen");
    callScreen->setMinimumHeight(430);
    callScreen->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
    auto *callScreenLayout = new QVBoxLayout;
    callScreenLayout->setContentsMargins(28, 26, 28, 26);
    callScreenLayout->setSpacing(16);
    callScreenPeer = new QLabel("Flutter 手机端");
    callScreenPeer->setObjectName("callScreenPeer");
    callScreenPeer->setAlignment(Qt::AlignCenter);
    callScreenTitle = new QLabel("语音通话");
    callScreenTitle->setObjectName("callScreenTitle");
    callScreenTitle->setAlignment(Qt::AlignCenter);
    callScreenState = new QLabel("呼叫中");
    callScreenState->setObjectName("callScreenState");
    callScreenState->setAlignment(Qt::AlignCenter);
    callScreenAvatar = new QLabel("IM");
    callScreenAvatar->setObjectName("callAvatar");
    callScreenAvatar->setAlignment(Qt::AlignCenter);
    callScreenAvatar->setMinimumHeight(180);
#ifdef ENTERPRISE_IM_HAS_MULTIMEDIA
    cameraViewfinder = new QCameraViewfinder;
    cameraViewfinder->setMinimumHeight(260);
    cameraViewfinder->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
    cameraViewfinder->setVisible(false);
    callScreenLayout->addWidget(cameraViewfinder, 1);
#endif
    callScreenHint = new QLabel("等待对方接听");
    callScreenHint->setObjectName("callScreenHint");
    callScreenHint->setAlignment(Qt::AlignCenter);
    callScreenHint->setWordWrap(true);
    callScreenAnswerButton = new QPushButton("接听");
    callScreenRejectButton = new QPushButton("拒绝");
    callScreenHangupButton = new QPushButton("挂断");
    callScreenAnswerButton->setObjectName("acceptCallButton");
    callScreenRejectButton->setObjectName("callDangerButton");
    callScreenHangupButton->setObjectName("callDangerButton");
    auto *callScreenButtons = new QHBoxLayout;
    callScreenButtons->setSpacing(20);
    callScreenButtons->addStretch();
    callScreenButtons->addWidget(callScreenAnswerButton);
    callScreenButtons->addWidget(callScreenRejectButton);
    callScreenButtons->addWidget(callScreenHangupButton);
    callScreenButtons->addStretch();
    callScreenLayout->addWidget(callScreenPeer);
    callScreenLayout->addWidget(callScreenTitle);
    callScreenLayout->addWidget(callScreenState);
    callScreenLayout->addWidget(callScreenAvatar, 1);
    callScreenLayout->addWidget(callScreenHint);
    callScreenLayout->addLayout(callScreenButtons);
    callScreen->setLayout(callScreenLayout);
    callScreen->setVisible(false);

    callPanel = new QWidget;
    callPanel->setObjectName("callPanel");
    callPanel->setMinimumWidth(0);
    callPanel->setMaximumWidth(780);
    callPanel->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    auto *callPanelLayout = new QVBoxLayout;
    callPanelLayout->setContentsMargins(14, 12, 14, 12);
    callPanelLayout->setSpacing(10);
    auto *callTitle = new QLabel("通话操作");
    callTitle->setObjectName("sideTitle");
    callPanelLayout->addWidget(callTitle);
    callPanelLayout->addLayout(callButtons);
    callPanel->setLayout(callPanelLayout);
    callPanel->setFixedHeight(220);
    callPanel->setVisible(false);

    auto *rightLayout = new QVBoxLayout;
    rightLayout->setContentsMargins(24, 20, 24, 18);
    rightLayout->setSpacing(10);
    rightLayout->addLayout(chatHeader);
    rightLayout->addWidget(actionStatusLabel);
#ifdef ENTERPRISE_IM_HAS_WEBENGINE
    auto *webPreview = new QWebEngineView;
    webPreview->setHtml("<html><body style='font-family:Microsoft YaHei;padding:12px;color:#334155;background:#f8fafc'><b>企业工作台</b><br/>WebEngine 已启用，可承载公告、文件预览或内嵌业务页。</body></html>");
    webPreview->setFixedHeight(58);
    webPreview->setMinimumWidth(0);
    webPreview->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    rightLayout->addWidget(webPreview);
#endif
    rightLayout->addWidget(callScreen, 1);
    rightLayout->addWidget(messageList);
    rightLayout->addWidget(composerPanel);
    rightLayout->addWidget(callPanel);
    rightLayout->addWidget(debugToggleButton);
    eventView->setMaximumHeight(180);
    rightLayout->addWidget(eventView);
    rightLayout->addStretch(1);

    auto *rightPanel = new QWidget;
    rightPanel->setMinimumWidth(0);
    rightPanel->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Preferred);
    rightPanel->setLayout(rightLayout);

    auto *splitter = new QSplitter;
    splitter->addWidget(leftPanel);
    splitter->addWidget(rightPanel);
    splitter->setStretchFactor(0, 0);
    splitter->setStretchFactor(1, 1);
    splitter->setSizes(QList<int>() << 320 << 900);

    auto *root = new QWidget;
    auto *layout = new QVBoxLayout;
    layout->setContentsMargins(0, 0, 0, 0);
    layout->addWidget(splitter);
    root->setLayout(layout);
    setCentralWidget(root);

    setStyleSheet(
        "QMainWindow{background:#f4f7fb;} QWidget{font-family:'Microsoft YaHei','Segoe UI';font-size:15px;color:#172033;}"
        "#leftPanel{background:#eef3f8;border-right:1px solid #d8e1ec;}"
        "#brand{font-size:28px;font-weight:700;color:#111827;padding:6px 0 0 0;}"
        "#sectionTitle{font-size:22px;font-weight:700;color:#111827;}"
        "#sideTitle{font-size:14px;font-weight:700;color:#52627a;padding:6px 0 0 0;}"
        "#statusPill{font-size:14px;color:#42526b;padding:4px 0 8px 0;}"
        "#notice{font-size:15px;padding:12px 14px;border:1px solid #b9d7ff;border-radius:6px;background:#eef6ff;color:#1f5fbf;}"
        "#callPanel{border:1px solid #d6dde8;border-radius:8px;background:#ffffff;}"
        "#callScreen{background:#111827;border-radius:8px;}"
        "#callScreenPeer{font-size:18px;font-weight:600;color:#e5e7eb;}"
        "#callScreenTitle{font-size:32px;font-weight:700;color:#ffffff;}"
        "#callScreenState{font-size:16px;color:#93c5fd;}"
        "#callScreenHint{font-size:14px;color:#cbd5e1;}"
        "#callAvatar{font-size:56px;font-weight:800;color:#ffffff;background:#2563eb;border-radius:8px;}"
        "QPushButton#acceptCallButton{font-size:18px;font-weight:700;min-width:112px;min-height:46px;border-radius:23px;background:#16a34a;border:1px solid #16a34a;color:#ffffff;}"
        "QPushButton#callDangerButton{font-size:18px;font-weight:700;min-width:112px;min-height:46px;border-radius:23px;background:#dc2626;border:1px solid #dc2626;color:#ffffff;}"
        "QLineEdit{font-size:15px;padding:10px 11px;border:1px solid #cfd8e6;border-radius:6px;background:#ffffff;selection-background-color:#2563eb;}"
        "QLineEdit:focus{border-color:#4f7fd9;background:#ffffff;}"
        "QPushButton{font-size:15px;font-weight:600;min-height:34px;padding:8px 14px;border:1px solid #c5d0df;border-radius:6px;background:#ffffff;color:#172033;}"
        "QPushButton:hover{background:#edf5ff;border-color:#6096e8;} QPushButton:pressed{background:#dcecff;}"
        "QPushButton:disabled{color:#9aa7b8;background:#f7f9fc;border-color:#dbe3ee;}"
        "QPushButton#primaryButton{background:#2f5ea8;border-color:#2f5ea8;color:#ffffff;}"
        "QPushButton#primaryButton:hover{background:#244f94;border-color:#244f94;}"
        "QPushButton#dangerButton{color:#b42318;border-color:#efc5c1;background:#fff8f7;}"
        "QListWidget,QTextEdit{border:1px solid #d6dde8;border-radius:8px;background:#ffffff;padding:8px;}"
        "QListWidget::item{font-size:15px;padding:13px 12px;border-bottom:1px solid #eef2f7;}"
        "#messageList{font-size:16px;alternate-background-color:#fafcff;}"
        "#messageList::item{font-size:16px;padding:16px 18px;border-bottom:1px solid #eef2f7;}"
        "QListWidget::item:selected{background:#dbeafe;color:#1e4fb3;border-radius:6px;}"
        "QSplitter::handle{background:#dde6f1;width:6px;}");

    connect(connectButton, &QPushButton::clicked, this, &MainWindow::connectToServer);
    connect(topAudioButton, &QPushButton::clicked, this, &MainWindow::startAudioCall);
    connect(topVideoButton, &QPushButton::clicked, this, &MainWindow::startVideoCall);
    connect(topHangupButton, &QPushButton::clicked, this, &MainWindow::hangupCall);
    connect(settingsButton, &QPushButton::clicked, this, [configPanel, configTitle]() {
        const bool show = !configPanel->isVisible();
        configPanel->setVisible(show);
        configTitle->setVisible(show);
    });
    connect(authButton, &QPushButton::clicked, this, &MainWindow::sendAuth);
    connect(pingButton, &QPushButton::clicked, this, &MainWindow::sendPing);
    connect(sendButton, &QPushButton::clicked, this, &MainWindow::sendText);
    connect(readinessButton, &QPushButton::clicked, this, &MainWindow::checkCallReadiness);
    connect(audioButton, &QPushButton::clicked, this, &MainWindow::startAudioCall);
    connect(videoButton, &QPushButton::clicked, this, &MainWindow::startVideoCall);
    connect(answerButton, &QPushButton::clicked, this, &MainWindow::answerCall);
    connect(rejectButton, &QPushButton::clicked, this, &MainWindow::rejectCall);
    connect(hangupButton, &QPushButton::clicked, this, &MainWindow::hangupCall);
    connect(callScreenAnswerButton, &QPushButton::clicked, this, &MainWindow::answerCall);
    connect(callScreenRejectButton, &QPushButton::clicked, this, &MainWindow::rejectCall);
    connect(callScreenHangupButton, &QPushButton::clicked, this, &MainWindow::hangupCall);
    connect(historyButton, &QPushButton::clicked, this, &MainWindow::loadCallHistory);
    connect(debugToggleButton, &QPushButton::clicked, this, [this]() {
        debugVisible = !debugVisible;
        eventView->setVisible(debugVisible);
        debugToggleButton->setText(debugVisible ? "隐藏调试日志" : "显示调试日志");
    });
    connect(sipMediaClient, &SipMediaClient::logLine, this, &MainWindow::appendLog);
    connect(conversationList, &QListWidget::currentTextChanged, this, [this](const QString &text) {
        if (text.contains("u_flutter")) {
            peerIdEdit->setText("u_flutter");
            conversationIdEdit->setText("c_qt_flutter");
            messageList->addItem("系统  已切换到 Flutter 手机端");
        } else if (text.contains("c_team")) {
            peerIdEdit->setText("u_web");
            conversationIdEdit->setText("c_team");
            messageList->addItem("系统  已切换到企业群聊");
        } else {
            peerIdEdit->setText("u_web");
            conversationIdEdit->setText("c_qt_web");
            messageList->addItem("系统  已切换到 Web 客户端");
        }
        setActionStatus("已切换会话：" + text);
    });
    connect(&socket, &QTcpSocket::readyRead, this, &MainWindow::readSocket);
    connect(&socket, &QTcpSocket::connected, this, &MainWindow::updateConnectionState);
    connect(&socket, &QTcpSocket::disconnected, this, &MainWindow::updateConnectionState);
    connect(&socket, static_cast<void (QTcpSocket::*)(QAbstractSocket::SocketError)>(&QTcpSocket::error),
            this, [this]() {
                setActionStatus("连接失败：" + socket.errorString(), true);
                appendLog("连接失败：" + socket.errorString());
            });

    updateConnectionState();
    refreshCallControls();
}

void MainWindow::connectToServer()
{
    if (socket.state() == QAbstractSocket::ConnectedState) {
        setActionStatus("正在断开连接...");
        socket.disconnectFromHost();
        return;
    }

    pending.clear();
    setActionStatus("正在连接服务端 " + hostEdit->text() + ":" + tcpPortEdit->text() + " ...");
    appendLog("连接 TCP " + hostEdit->text() + ":" + tcpPortEdit->text());
    socket.connectToHost(hostEdit->text(), tcpPortEdit->text().toUShort());
}

void MainWindow::sendAuth()
{
    QJsonObject payload;
    payload.insert("token", tokenEdit->text());
    setActionStatus("正在登录认证...");
    sendFrame("AUTH", QString(), QString(), payload);
}

void MainWindow::sendPing()
{
    setActionStatus("正在发送心跳...");
    sendFrame("PING", QString(), QString(), QJsonObject());
}

void MainWindow::sendText()
{
    const QString content = messageEdit->text().trimmed();
    if (content.isEmpty()) {
        setActionStatus("消息不能为空", true);
        return;
    }

    QJsonObject payload;
    payload.insert("content", content);
    sendFrame("TEXT", peerIdEdit->text(), conversationIdEdit->text(), payload);
}

void MainWindow::checkCallReadiness()
{
    setActionStatus("正在检测服务端音视频配置...");
    apiGet("/api/calls/readiness");
}

void MainWindow::startAudioCall()
{
    startCall("audio");
}

void MainWindow::startVideoCall()
{
    startCall("video");
}

void MainWindow::answerCall()
{
    transitionCall("answer");
}

void MainWindow::rejectCall()
{
    transitionCall("reject");
}

void MainWindow::hangupCall()
{
    sipMediaClient->stop();
    nativeStartedCallId.clear();
    transitionCall("hangup");
}

void MainWindow::loadCallHistory()
{
    QUrlQuery query;
    query.addQueryItem("userId", userIdEdit->text());
    query.addQueryItem("limit", "20");
    setActionStatus("正在读取通话记录...");
    apiGet("/api/calls?" + query.toString());
}

void MainWindow::readSocket()
{
    pending.append(socket.readAll());
    int newline = pending.indexOf('\n');
    while (newline >= 0) {
        const QByteArray line = pending.left(newline).trimmed();
        pending.remove(0, newline + 1);
        if (!line.isEmpty()) {
            handleSocketLine(line);
        }
        newline = pending.indexOf('\n');
    }
}

void MainWindow::handleSocketLine(const QByteArray &line)
{
    const QJsonDocument doc = QJsonDocument::fromJson(line);
    if (!doc.isObject()) {
        appendLog("收到无法识别的服务端消息");
        return;
    }

    const QJsonObject frame = doc.object();
    const QString type = frame.value("type").toString();
    const QJsonObject payload = frame.value("payload").toObject();

    if (type == "AUTH_OK") {
        setActionStatus("登录认证成功，可以发送消息。");
        appendMessage("系统", "桌面端已登录：" + frame.value("to").toString(userIdEdit->text()));
    } else if (type == "PONG") {
        setActionStatus("心跳成功，当前连接正常。");
    } else if (type == "ACK") {
        setActionStatus("服务端已收到刚才的操作。");
    } else if (type == "TEXT_DELIVER") {
        appendMessage(frame.value("from").toString("对方"), payload.value("content").toString());
        setActionStatus("收到来自 " + frame.value("from").toString("对方") + " 的消息。");
    } else if ((type == "CALL_INVITE" || type == "CALL_UPDATE") && payload.contains("id")) {
        activeCallId = payload.value("id").toString();
        activeMediaType = payload.value("mediaType").toString();
        activeCallIncoming = payload.value("calleeId").toString() == userIdEdit->text();
        updateCallStatus(activeMediaType + " / " + payload.value("status").toString());
        if (payload.value("status").toString() == "answered" && payload.value("mediaStatus").toString() == "media_ready" && nativeStartedCallId != activeCallId) {
            requestMediaConfig();
        }
        if (payload.value("status").toString() == "rejected" || payload.value("status").toString() == "ended") {
            sipMediaClient->stop();
            nativeStartedCallId.clear();
            stopLocalCameraPreview();
        }
        setActionStatus(type == "CALL_INVITE" ? "收到通话邀请，可点击接听或拒绝。" : "通话状态已更新。");
    } else if (type == "ERROR") {
        setActionStatus("服务端返回错误：" + payload.value("message").toString(), true);
    } else {
        setActionStatus("收到服务端消息：" + type);
    }

    appendLog("收到 " + type + "：" + QString::fromUtf8(line));
}

void MainWindow::updateConnectionState()
{
    const bool connected = socket.state() == QAbstractSocket::ConnectedState;
    connectButton->setText(connected ? "断开" : "连接");
    statusLabel->setText(connected ? "在线 - TCP 已连接" : "离线 - TCP 未连接");
    authButton->setEnabled(connected);
    pingButton->setEnabled(connected);
    sendButton->setEnabled(connected);
    if (connected) {
        setActionStatus("TCP 已连接。下一步点“登录认证”。");
        appendMessage("系统", "TCP 已连接到服务端");
    } else {
        setActionStatus("TCP 未连接。请先点击“连接”。");
    }
    appendLog(connected ? "TCP 已连接" : "TCP 已断开");
}

void MainWindow::appendLog(const QString &line)
{
    QString text = line;
    if (line.contains("PJSUA_BIN empty") || line.contains("找不到指定的文件")) {
        text = "桌面端本机没有找到 PJSIP/pjsua，真实语音视频无法启动。信令和通话记录仍然可演示。";
        setActionStatus(text, true);
    } else if (line.startsWith("SIP START")) {
        text = "正在启动桌面端 SIP 媒体进程...";
    } else if (line.contains("registration success")) {
        text = "桌面端 SIP 注册成功。";
        setActionStatus(text);
        updateCallScreen();
    } else if (line.contains("Call 0 state changed to CONFIRMED") || line.contains("Call 0 state changed to C")) {
        text = "桌面端 SIP 通话已接通。";
        setActionStatus(text);
        updateCallScreen();
    } else if (line.contains("Audio updated") || line.contains("PCMU (sendrecv)")) {
        text = "桌面端音频 RTP 已建立。";
        setActionStatus(text);
        callScreenHint->setText(activeMediaType == "video"
            ? "视频通道已启动。若没有远端画面，请确认手机端摄像头权限和 pjsua2 视频渲染。"
            : "音频 RTP 已建立。若听不到声音，请检查系统输入/输出设备和手机音量。");
    } else if (line.startsWith("SIP STOP")) {
        text = "正在停止桌面端 SIP 媒体进程...";
    } else if (line.startsWith("SIP EXIT")) {
        text = "桌面端 SIP 媒体进程已退出。";
    }
    eventView->append(QDateTime::currentDateTime().toString("HH:mm:ss ") + text);
}

void MainWindow::appendMessage(const QString &sender, const QString &content)
{
    QString prefix = "系统";
    if (sender == userIdEdit->text()) {
        prefix = "我";
    } else if (sender != "系统") {
        prefix = sender;
    }
    messageList->addItem(prefix + "\n" + content);
    messageList->scrollToBottom();
    saveLocalMessage(sender, content);
}

void MainWindow::updateCallStatus(const QString &status)
{
    QStringList parts = status.split(" / ");
    const QString media = parts.isEmpty() ? QString() : parts.at(0);
    const QString state = parts.size() > 1 ? parts.at(1) : status;
    activeCallStatus = state;
    refreshCallControls();
    callStatusLabel->setText("通话：" + mediaTypeText(media) + " / " + callStatusText(state));
}

void MainWindow::setActionStatus(const QString &text, bool error)
{
    actionStatusLabel->setText(text);
    actionStatusLabel->setStyleSheet(error
        ? "color:#b91c1c;background:#fff1f2;border:1px solid #fecdd3;border-radius:6px;padding:8px;"
        : "color:#075985;background:#eff6ff;border:1px solid #bfdbfe;border-radius:6px;padding:8px;");
}

void MainWindow::refreshCallControls()
{
    const bool hasCall = !activeCallId.isEmpty();
    const bool ringing = activeCallStatus == "ringing";
    const bool answered = activeCallStatus == "answered";
    const bool incoming = hasCall && ringing && activeCallIncoming;
    const bool outgoing = hasCall && ringing && !activeCallIncoming;

    callPanel->setVisible(incoming || outgoing || answered);
    answerButton->setVisible(incoming);
    rejectButton->setVisible(incoming);
    hangupButton->setVisible(hasCall && (ringing || answered));
    audioButton->setEnabled(!hasCall || activeCallStatus == "rejected" || activeCallStatus == "ended");
    videoButton->setEnabled(!hasCall || activeCallStatus == "rejected" || activeCallStatus == "ended");
    if (hasCall && (ringing || answered)) {
        showCallScreen();
    } else {
        hideCallScreen();
    }
    updateCallScreen();
}

void MainWindow::showCallScreen()
{
    callScreen->setVisible(true);
    messageList->setVisible(false);
    composerPanel->setVisible(false);
    callPanel->setVisible(false);
}

void MainWindow::hideCallScreen()
{
    callScreen->setVisible(false);
    messageList->setVisible(true);
    composerPanel->setVisible(true);
    stopLocalCameraPreview();
}

void MainWindow::updateCallScreen()
{
    const bool incoming = !activeCallId.isEmpty() && activeCallIncoming && activeCallStatus == "ringing";
    const bool answered = activeCallStatus == "answered";
    const bool video = activeMediaType == "video";

    callScreenPeer->setText(activeCallIncoming ? peerIdEdit->text() + " 来电" : "正在呼叫 " + peerIdEdit->text());
    callScreenTitle->setText(video ? "视频通话" : "语音通话");
    callScreenState->setText(callStatusText(activeCallStatus));
    callScreenAnswerButton->setVisible(incoming);
    callScreenRejectButton->setVisible(incoming);
    callScreenHangupButton->setVisible(!activeCallId.isEmpty() && (activeCallStatus == "ringing" || answered));
    callScreenAvatar->setVisible(!video);
    if (video) {
        startLocalCameraPreview();
        callScreenHint->setText(answered
            ? "本机摄像头预览已打开。远端画面由 PJSIP 视频窗口/手机端渲染能力决定。"
            : "等待接听；本机摄像头预览已准备。");
    } else {
        stopLocalCameraPreview();
        callScreenHint->setText(answered
            ? "语音通话中；若无声音，请检查电脑麦克风/扬声器和手机音量。"
            : (incoming ? "对方请求语音通话" : "等待对方接听"));
    }
}

void MainWindow::startLocalCameraPreview()
{
#ifdef ENTERPRISE_IM_HAS_MULTIMEDIA
    if (!cameraViewfinder) {
        return;
    }
    cameraViewfinder->setVisible(true);
    if (camera && camera->state() == QCamera::ActiveState) {
        return;
    }
    if (!camera) {
        const QList<QCameraInfo> cameras = QCameraInfo::availableCameras();
        if (cameras.isEmpty()) {
            callScreenHint->setText("未发现本机摄像头。请检查摄像头权限/占用。");
            cameraViewfinder->setVisible(false);
            return;
        }
        camera = new QCamera(cameras.first(), this);
        camera->setViewfinder(cameraViewfinder);
    }
    camera->start();
#else
    callScreenHint->setText("当前 Qt 包未启用 MultimediaWidgets，无法在应用内显示摄像头预览。");
#endif
}

void MainWindow::stopLocalCameraPreview()
{
#ifdef ENTERPRISE_IM_HAS_MULTIMEDIA
    if (camera) {
        camera->stop();
    }
    if (cameraViewfinder) {
        cameraViewfinder->setVisible(false);
    }
#endif
}

QString MainWindow::callStatusText(const QString &status) const
{
    if (status == "ringing") return "呼叫中";
    if (status == "answered") return "已接听";
    if (status == "rejected") return "已拒绝";
    if (status == "ended") return "已挂断";
    if (status.isEmpty()) return "未开始";
    return status;
}

QString MainWindow::mediaTypeText(const QString &mediaType) const
{
    if (mediaType == "audio") return "语音";
    if (mediaType == "video") return "视频";
    if (mediaType.isEmpty()) return "-";
    return mediaType;
}

void MainWindow::sendFrame(const QString &type, const QString &to, const QString &conversationId, const QJsonObject &payload)
{
    if (socket.state() != QAbstractSocket::ConnectedState) {
        setActionStatus("还没连接服务端，请先点击“连接”。", true);
        return;
    }

    QJsonObject frame;
    frame.insert("version", "1");
    frame.insert("type", type);
    QString requestId = QUuid::createUuid().toString();
    requestId.remove('{').remove('}');
    frame.insert("requestId", requestId);
    frame.insert("from", userIdEdit->text());
    if (!to.isEmpty()) {
        frame.insert("to", to);
    }
    if (!conversationId.isEmpty()) {
        frame.insert("conversationId", conversationId);
    }
    frame.insert("timestamp", QDateTime::currentMSecsSinceEpoch());
    frame.insert("payload", payload);

    const QByteArray json = QJsonDocument(frame).toJson(QJsonDocument::Compact);
    socket.write(json);
    socket.write("\n");
    socket.flush();

    if (type == "TEXT") {
        appendMessage(userIdEdit->text(), payload.value("content").toString());
        messageEdit->clear();
        setActionStatus("消息已发送，等待服务端确认。");
    }
    appendLog("发送 " + type + "：" + QString::fromUtf8(json));
}

void MainWindow::startCall(const QString &mediaType)
{
    activeMediaType = mediaType;
    activeCallIncoming = false;
    activeCallStatus = "ringing";
    refreshCallControls();
    QJsonObject body;
    body.insert("callerId", userIdEdit->text());
    body.insert("calleeId", peerIdEdit->text());
    body.insert("conversationId", conversationIdEdit->text());
    body.insert("mediaType", mediaType);
    setActionStatus("正在发起" + mediaTypeText(mediaType) + "通话...");
    apiPost("/api/calls", body);
}

void MainWindow::transitionCall(const QString &action)
{
    if (activeCallId.isEmpty()) {
        setActionStatus("当前没有可操作的通话。请先发起通话，或等待别人呼入。", true);
        return;
    }
    QJsonObject body;
    body.insert("actorId", userIdEdit->text());

    if (action == "answer") setActionStatus("正在接听通话...");
    if (action == "reject") setActionStatus("正在拒绝通话...");
    if (action == "hangup") setActionStatus("正在挂断通话...");
    if (action == "reject" || action == "hangup") {
        stopLocalCameraPreview();
    }
    apiPost("/api/calls/" + activeCallId + "/" + action, body);
}

void MainWindow::requestMediaConfig()
{
    QUrlQuery query;
    query.addQueryItem("userId", userIdEdit->text());
    query.addQueryItem("calleeId", peerIdEdit->text());
    apiGet("/api/calls/media-config?" + query.toString());
}

void MainWindow::apiGet(const QString &path)
{
    sendApiRequest("GET", path, QJsonObject());
}

void MainWindow::apiPost(const QString &path, const QJsonObject &body)
{
    sendApiRequest("POST", path, body);
}

void MainWindow::sendApiRequest(const QString &method, const QString &path, const QJsonObject &body)
{
    const QUrl url(baseUrl() + path);
    QNetworkRequest request(url);
    request.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    const QString token = tokenEdit->text().trimmed();
    if (!token.isEmpty()) {
        request.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
    }

    appendLog("请求接口 " + method + " " + url.toString());
    QNetworkReply *reply = nullptr;
    if (method == "POST") {
        reply = http.post(request, QJsonDocument(body).toJson(QJsonDocument::Compact));
    } else {
        reply = http.get(request);
    }

    connect(reply, &QNetworkReply::finished, this, [this, reply, method, path]() {
        const QByteArray body = reply->readAll();
        if (reply->error() != QNetworkReply::NoError) {
            setActionStatus("接口请求失败：" + reply->errorString(), true);
            appendLog("接口失败 " + reply->errorString() + " " + QString::fromUtf8(body));
        } else {
            handleApiResponse(method + " " + path, body);
        }
        reply->deleteLater();
    });
}

void MainWindow::handleApiResponse(const QString &label, const QByteArray &body)
{
    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject()) {
        setActionStatus("服务端返回格式错误。", true);
        appendLog("接口返回不是 JSON：" + QString::fromUtf8(body));
        return;
    }

    const QJsonObject root = doc.object();
    if (!root.value("success").toBool()) {
        setActionStatus("操作失败：" + root.value("error").toString(), true);
        appendLog("接口业务失败 " + label + " " + root.value("error").toString());
        return;
    }

    const QJsonValue data = root.value("data");
    if (data.isObject()) {
        const QJsonObject object = data.toObject();
        const QString callId = object.value("id").toString();

        if (object.contains("ready")) {
            setActionStatus(object.value("ready").toBool()
                ? "音视频服务已就绪，可以发起语音/视频信令。"
                : "音视频服务未完全就绪，请查看调试日志。", !object.value("ready").toBool());
        }

        if (!callId.isEmpty()) {
            activeCallId = callId;
            activeMediaType = object.value("mediaType").toString();
            const QString status = object.value("status").toString();
            if (object.contains("calleeId")) {
                activeCallIncoming = object.value("calleeId").toString() == userIdEdit->text();
            }
            updateCallStatus(activeMediaType + " / " + status);
            setActionStatus(mediaTypeText(activeMediaType) + "通话状态：" + callStatusText(status));
            saveLocalCall(callId, activeMediaType, status);
            if (status == "answered" && object.value("mediaStatus").toString() == "media_ready" && nativeStartedCallId != activeCallId) {
                requestMediaConfig();
            }
            if (status == "rejected" || status == "ended") {
                sipMediaClient->stop();
                nativeStartedCallId.clear();
                stopLocalCameraPreview();
            }
        }

        if (object.contains("sipUsername")) {
            setActionStatus("已拿到 SIP 媒体配置，正在尝试启动桌面端音视频。");
            nativeStartedCallId = activeCallId;
            sipMediaClient->start(object, activeCallId, activeMediaType, !activeCallIncoming);
        }

        appendLog("接口成功 " + label + " " + QString::fromUtf8(QJsonDocument(object).toJson(QJsonDocument::Compact)));
        return;
    }

    if (data.isArray()) {
        const QJsonArray items = data.toArray();
        if (!items.isEmpty() && items.at(0).isObject()) {
            activeCallId = items.at(0).toObject().value("id").toString();
        }
        setActionStatus("已读取通话记录：" + QString::number(items.size()) + " 条。");
        appendLog("接口成功 " + label + " 条数=" + QString::number(items.size()));
        return;
    }

    setActionStatus("操作成功。");
    appendLog("接口成功 " + label);
}

QString MainWindow::baseUrl() const
{
    return "http://" + hostEdit->text().trimmed() + ":" + httpPortEdit->text().trimmed();
}

void MainWindow::initLocalStore()
{
    QSqlDatabase db = QSqlDatabase::addDatabase("QSQLITE");
    db.setDatabaseName("enterprise-im-desktop.sqlite");
    if (!db.open()) {
        appendLog("SQLite 打开失败：" + db.lastError().text());
        return;
    }

    QSqlQuery query(db);
    query.exec("CREATE TABLE IF NOT EXISTS local_messages (id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT NOT NULL, content TEXT NOT NULL, created_at TEXT NOT NULL)");
    query.exec("CREATE TABLE IF NOT EXISTS local_calls (id INTEGER PRIMARY KEY AUTOINCREMENT, call_id TEXT NOT NULL, media_type TEXT, status TEXT, created_at TEXT NOT NULL)");
    appendLog("SQLite 本地缓存已启用：enterprise-im-desktop.sqlite");
}

void MainWindow::saveLocalMessage(const QString &sender, const QString &content)
{
    QSqlDatabase db = QSqlDatabase::database();
    if (!db.isOpen()) {
        return;
    }
    QSqlQuery query(db);
    query.prepare("INSERT INTO local_messages(sender, content, created_at) VALUES(?, ?, ?)");
    query.addBindValue(sender);
    query.addBindValue(content);
    query.addBindValue(QDateTime::currentDateTime().toString(Qt::ISODate));
    query.exec();
}

void MainWindow::saveLocalCall(const QString &callId, const QString &mediaType, const QString &status)
{
    QSqlDatabase db = QSqlDatabase::database();
    if (!db.isOpen() || callId.isEmpty()) {
        return;
    }
    QSqlQuery query(db);
    query.prepare("INSERT INTO local_calls(call_id, media_type, status, created_at) VALUES(?, ?, ?, ?)");
    query.addBindValue(callId);
    query.addBindValue(mediaType);
    query.addBindValue(status);
    query.addBindValue(QDateTime::currentDateTime().toString(Qt::ISODate));
    query.exec();
}
