#include "MainWindow.h"
#include "SipMediaClient.h"

#include <QApplication>
#include <QCheckBox>
#include <QClipboard>
#include <QCloseEvent>
#include <QComboBox>
#include <QCoreApplication>
#include <QDateTime>
#include <QDesktopServices>
#include <QDialog>
#include <QDialogButtonBox>
#include <QDir>
#include <QFile>
#include <QFileDialog>
#include <QFileInfo>
#include <QFormLayout>
#include <QGridLayout>
#include <QGroupBox>
#include <QHBoxLayout>
#include <QImage>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonValue>
#include <QLabel>
#include <QLineEdit>
#include <QListWidget>
#include <QListWidgetItem>
#include <QInputDialog>
#include <QMenu>
#include <QMessageBox>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QHttpMultiPart>
#include <QPainter>
#include <QProgressDialog>
#include <QPushButton>
#include <QRegularExpression>
#include <QScrollArea>
#include <QSizePolicy>
#include <QSpinBox>
#include <QSqlDatabase>
#include <QSqlError>
#include <QSqlQuery>
#include <QSplitter>
#include <QStackedWidget>
#include <QTabWidget>
#include <QTextBrowser>
#include <QTreeWidget>
#include <QTextCursor>
#include <QTextEdit>
#include <QTextStream>
#include <QTimer>
#include <QTransform>
#include <QUrl>
#include <QUrlQuery>
#include <QUuid>
#include <QVBoxLayout>
#include <QWidget>

#ifdef Q_OS_WIN
#define NOMINMAX
#include <windows.h>
#include <mmsystem.h>
#pragma comment(lib, "winmm.lib")
#endif

#ifdef ENTERPRISE_IM_HAS_MULTIMEDIA
#include <QCamera>
#include <QCameraInfo>
#include <QCameraViewfinder>
#include <QMediaPlayer>
#endif

#ifdef ENTERPRISE_IM_HAS_WEBENGINE
#include <QWebEngineView>
#endif

static QString readableMessageContent(const QString &msgType, const QString &content)
{
    const QByteArray raw = content.toUtf8();
    const QJsonDocument doc = QJsonDocument::fromJson(raw);
    const QJsonObject obj = doc.isObject() ? doc.object() : QJsonObject();

    if (msgType == "location") {
        const QString name = obj.value("name").toString("Location");
        const QString address = obj.value("address").toString();
        const QString lat = obj.contains("latitude") ? QString::number(obj.value("latitude").toDouble(), 'f', 6) : QString();
        const QString lng = obj.contains("longitude") ? QString::number(obj.value("longitude").toDouble(), 'f', 6) : QString();
        QStringList lines;
        lines << ("Location: " + name);
        if (!address.isEmpty()) lines << address;
        if (!lat.isEmpty() && !lng.isEmpty()) lines << (lat + ", " + lng);
        return lines.join("\n");
    }

    if (msgType == "card") {
        const QString name = obj.value("name").toString(obj.value("displayName").toString("Contact Card"));
        const QString userId = obj.value("userId").toString(obj.value("id").toString());
        const QString signature = obj.value("signature").toString();
        QStringList lines;
        lines << ("Contact: " + name);
        if (!userId.isEmpty()) lines << ("ID: " + userId);
        if (!signature.isEmpty()) lines << signature;
        return lines.join("\n");
    }

    if (msgType == "voice") return content.isEmpty() ? "Voice message" : content;
    if (msgType == "video") return content.isEmpty() ? "Video message" : content;
    return content;
}

static QString smartTimeFormat(const QString &isoTimestamp)
{
    if (isoTimestamp.isEmpty()) return QString();
    const QDateTime dt = QDateTime::fromString(isoTimestamp, Qt::ISODate);
    if (!dt.isValid()) {
        // Try parsing as Unix timestamp (seconds or millis)
        bool ok = false;
        qint64 ts = isoTimestamp.toLongLong(&ok);
        if (ok) {
            if (ts > 1e12) ts /= 1000;
            return smartTimeFormat(QDateTime::fromSecsSinceEpoch(ts).toString(Qt::ISODate));
        }
        return isoTimestamp.left(16);
    }
    const QDate today = QDate::currentDate();
    const QDate msgDate = dt.date();
    if (msgDate == today) {
        return dt.toString("HH:mm");
    } else if (msgDate == today.addDays(-1)) {
        return QString::fromUtf8("昨天");
    } else if (msgDate.weekNumber() == today.weekNumber() && msgDate.year() == today.year()) {
        return dt.toString("ddd");
    } else if (msgDate.year() == today.year()) {
        return dt.toString("MM/dd");
    }
    return dt.toString("yyyy/MM/dd");
}

static QString autoLinkUrls(const QString &text)
{
    // Match http/https/ftp URLs — exclude trailing punctuation like .,;:!?)
    QRegularExpression re("(https?://[^\\s<>\"'\\[\\]()，。；：！？、]+[^\\s<>\"'\\[\\]().,;:!?，。；：！？、)])");
    QString result;
    int lastEnd = 0;
    QRegularExpressionMatchIterator it = re.globalMatch(text);
    while (it.hasNext()) {
        QRegularExpressionMatch m = it.next();
        if (m.capturedStart() > lastEnd) {
            result += text.mid(lastEnd, m.capturedStart() - lastEnd).toHtmlEscaped();
        }
        const QString url = m.captured(1);
        result += "<a href='" + url.toHtmlEscaped() + "'>" + url.toHtmlEscaped() + "</a>";
        lastEnd = m.capturedEnd();
    }
    if (lastEnd < text.length()) {
        result += text.mid(lastEnd).toHtmlEscaped();
    }
    return result.isEmpty() ? text.toHtmlEscaped() : result;
}

// ---------------------------------------------------------------------------
// Voice recording context (Windows WaveIn)
// ---------------------------------------------------------------------------

#ifdef Q_OS_WIN
struct VoiceRecordCtx {
    HWAVEIN hWaveIn;
    WAVEHDR headers[4];
    QFile *file;
    DWORD totalBytes;
    bool active;
    char buffers[4][8192]; // 4 x 8KB buffers for double-buffering
};

static void CALLBACK waveInProc(HWAVEIN hwi, UINT uMsg, DWORD_PTR dwInstance,
                                 DWORD_PTR, DWORD_PTR dwParam1, DWORD_PTR)
{
    VoiceRecordCtx *ctx = reinterpret_cast<VoiceRecordCtx *>(dwInstance);
    if (!ctx || !ctx->active) return;

    if (uMsg == WIM_DATA) {
        LPWAVEHDR wh = reinterpret_cast<LPWAVEHDR>(dwParam1);
        if (wh->dwBytesRecorded > 0 && ctx->file && ctx->file->isOpen()) {
            ctx->file->write(reinterpret_cast<const char *>(wh->dwUser),
                             wh->dwBytesRecorded);
            ctx->totalBytes += wh->dwBytesRecorded;
        }
        waveInUnprepareHeader(hwi, wh, sizeof(WAVEHDR));
        // Re-queue the buffer
        wh->dwBufferLength = 8192;
        wh->dwFlags = 0;
        wh->dwBytesRecorded = 0;
        waveInPrepareHeader(hwi, wh, sizeof(WAVEHDR));
        waveInAddBuffer(hwi, wh, sizeof(WAVEHDR));
    } else if (uMsg == WIM_CLOSE) {
        ctx->active = false;
    }
}
#endif

// ---------------------------------------------------------------------------
// Construction
// ---------------------------------------------------------------------------

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent),
      sipMediaClient(nullptr),
      activeCallIncoming(false),
      chunkUploadTotalBytes(0),
      chunkUploadTotalChunks(0),
      chunkUploadNextChunk(0),
      chunkUploadRetryCount(0),
      activeUploadQueueId(-1),
      uploadQueueResuming(false),
      voiceRecording(false),
      voiceRecordCtx(nullptr),
      debugVisible(false),
      darkMode(false),
      loggedIn(false),
      closing(false),
      reconnectAttempts(0),
      reconnectTimer(new QTimer(this)),
      typingTimer(new QTimer(this)),
      groupOnlineTimer(new QTimer(this)),
      settings("EnterpriseIM", "QtClient")
#ifdef ENTERPRISE_IM_HAS_MULTIMEDIA
      ,
      camera(nullptr),
      cameraViewfinder(nullptr)
#endif
{
    reconnectTimer->setSingleShot(true);
    setWindowTitle("企业即时通讯桌面端");
    resize(1360, 860);

    // --- Login panel (shown when not logged in) ---
    phoneEdit = new QLineEdit;
    phoneEdit->setPlaceholderText("手机号");
    passwordEdit = new QLineEdit;
    passwordEdit->setPlaceholderText("密码");
    passwordEdit->setEchoMode(QLineEdit::Password);
    loginButton = new QPushButton("登录");
    loginButton->setObjectName("primaryButton");
    loginStatusLabel = new QLabel;
    loginStatusLabel->setWordWrap(true);
    loginStatusLabel->setObjectName("notice");
    loginStatusLabel->setVisible(false);

    // Password visibility toggle
    auto *pwdLayout = new QHBoxLayout;
    pwdLayout->setContentsMargins(0, 0, 0, 0);
    pwdLayout->setSpacing(4);
    pwdLayout->addWidget(passwordEdit, 1);
    auto *pwdToggleBtn = new QPushButton;
    pwdToggleBtn->setFixedSize(36, 36);
    pwdToggleBtn->setText(QString::fromUtf8("\xe2\x97\x8f")); // U+25CF dot
    pwdToggleBtn->setFlat(true);
    pwdToggleBtn->setCursor(Qt::PointingHandCursor);
    pwdToggleBtn->setToolTip("显示/隐藏密码");
    connect(pwdToggleBtn, &QPushButton::clicked, this, [this, pwdToggleBtn]() {
        if (passwordEdit->echoMode() == QLineEdit::Password) {
            passwordEdit->setEchoMode(QLineEdit::Normal);
            pwdToggleBtn->setText(QString::fromUtf8("\xe2\x97\x8b")); // open circle
        } else {
            passwordEdit->setEchoMode(QLineEdit::Password);
            pwdToggleBtn->setText(QString::fromUtf8("\xe2\x97\x8f")); // filled dot
        }
    });
    pwdLayout->addWidget(pwdToggleBtn);

    // Login form widget
    auto *loginFormWidget = new QWidget;
    auto *loginFormLayout = new QVBoxLayout(loginFormWidget);
    loginFormLayout->setContentsMargins(22, 40, 22, 22);
    loginFormLayout->setSpacing(14);
    auto *loginBrand = new QLabel("企业 IM");
    loginBrand->setObjectName("brand");
    loginFormLayout->addWidget(loginBrand);
    loginFormLayout->addSpacing(20);
    auto *loginTitle = new QLabel("用户登录");
    loginTitle->setObjectName("sideTitle");
    loginFormLayout->addWidget(loginTitle);
    loginFormLayout->addWidget(phoneEdit);
    loginFormLayout->addLayout(pwdLayout);
    loginFormLayout->addWidget(loginButton);
    loginFormLayout->addWidget(loginStatusLabel);
    loginFormLayout->addStretch(1);

    // Onboarding pages
    auto *onboardStack = new QStackedWidget;
    for (int i = 0; i < 3; ++i) {
        auto *page = new QWidget;
        auto *pageLayout = new QVBoxLayout(page);
        pageLayout->setContentsMargins(40, 60, 40, 40);
        pageLayout->setSpacing(18);

        QLabel *iconLabel;
        QString title, desc;
        if (i == 0) {
            iconLabel = new QLabel(QString::fromUtf8("\xf0\x9f\x91\x8b")); // wave
            title = "欢迎使用企业 IM";
            desc = "安全、高效的企业即时通讯平台\n\n支持文字、图片、文件、音视频通话\n为团队协作提供全方位通讯保障";
        } else if (i == 1) {
            iconLabel = new QLabel(QString::fromUtf8("\xf0\x9f\x94\x90")); // lock
            title = "安全通讯";
            desc = "端到端加密保护您的每一次通讯\n\n阅后即焚、截屏通知、消息撤回\n企业级安全，值得信赖";
        } else {
            iconLabel = new QLabel(QString::fromUtf8("\xf0\x9f\x9a\x80")); // rocket
            title = "开始使用";
            desc = "登录您的账号，开始高效协作\n\n随时随地进行团队沟通\n提升工作效率，减少沟通成本";
        }
        iconLabel->setAlignment(Qt::AlignCenter);
        iconLabel->setStyleSheet("font-size:72px; border:none; background:transparent;");

        auto *titleLabel = new QLabel(title);
        titleLabel->setObjectName("brand");
        titleLabel->setAlignment(Qt::AlignCenter);
        titleLabel->setStyleSheet("font-size:26px;");

        auto *descLabel = new QLabel(desc);
        descLabel->setWordWrap(true);
        descLabel->setAlignment(Qt::AlignCenter);
        descLabel->setStyleSheet("font-size:16px; color:#6b7280; border:none; background:transparent;");

        pageLayout->addStretch(1);
        pageLayout->addWidget(iconLabel);
        pageLayout->addWidget(titleLabel);
        pageLayout->addWidget(descLabel);
        pageLayout->addStretch(1);

        if (i < 2) {
            auto *nextBtn = new QPushButton("下一步");
            nextBtn->setObjectName("primaryButton");
            nextBtn->setMinimumWidth(200);
            auto *btnRow = new QHBoxLayout;
            btnRow->addStretch();
            btnRow->addWidget(nextBtn);
            btnRow->addStretch();
            pageLayout->addLayout(btnRow);
            connect(nextBtn, &QPushButton::clicked, this, [onboardStack]() {
                onboardStack->setCurrentIndex(onboardStack->currentIndex() + 1);
            });
        } else {
            auto *startBtn = new QPushButton("开始使用");
            startBtn->setObjectName("primaryButton");
            startBtn->setMinimumWidth(200);
            auto *btnRow = new QHBoxLayout;
            btnRow->addStretch();
            btnRow->addWidget(startBtn);
            btnRow->addStretch();
            pageLayout->addLayout(btnRow);
            connect(startBtn, &QPushButton::clicked, this, [onboardStack]() {
                onboardStack->setCurrentIndex(3); // show login form
            });
        }

        onboardStack->addWidget(page);
    }
    onboardStack->addWidget(loginFormWidget); // index 3 = login form

    // Show onboarding first time, otherwise login form
    const bool firstLaunch = settings.value("app/firstLaunch", true).toBool();
    onboardStack->setCurrentIndex(firstLaunch ? 0 : 3);

    auto *loginPanelOuter = new QVBoxLayout;
    loginPanelOuter->setContentsMargins(0, 0, 0, 0);
    loginPanelOuter->addWidget(onboardStack);

    // --- Hidden connection config fields (used internally) ---
    hostEdit = new QLineEdit("127.0.0.1");
    httpPortEdit = new QLineEdit("18080");
    tcpPortEdit = new QLineEdit("19090");
    userIdEdit = new QLineEdit;
    tokenEdit = new QLineEdit;
    peerIdEdit = new QLineEdit;
    conversationIdEdit = new QLineEdit;

    // --- Session list panel (shown after login) ---
    conversationList = new QListWidget;
    conversationList->setObjectName("conversationList");
    conversationList->setContextMenuPolicy(Qt::CustomContextMenu);
    connect(conversationList, &QListWidget::customContextMenuRequested, this, &MainWindow::onConversationContextMenu);
    contactList = new QListWidget;
    contactList->setObjectName("contactList");
    friendRequestList = new QListWidget;
    friendRequestList->setObjectName("friendRequestList");
    searchEdit = new QLineEdit;
    searchEdit->setPlaceholderText("Search contacts, groups, messages, files");
    searchResultList = new QListWidget;
    searchResultList->setObjectName("searchResultList");

    logoutButton = new QPushButton("退出登录");
    logoutButton->setObjectName("dangerButton");
    settingsButton = new QPushButton("设置");
    statusLabel = new QLabel("离线");
    statusLabel->setObjectName("statusPill");

    auto *sessionLayout = new QVBoxLayout;
    sessionLayout->setContentsMargins(0, 0, 0, 0);
    sessionLayout->setSpacing(0);

    // Brand and status bar at top
    auto *topBar = new QVBoxLayout;
    topBar->setContentsMargins(22, 22, 18, 8);
    auto *sessionBrand = new QLabel("企业 IM");
    sessionBrand->setObjectName("brand");
    topBar->addWidget(sessionBrand);
    topBar->addWidget(statusLabel);
    auto *topBtnRow = new QHBoxLayout;
    topBtnRow->setSpacing(8);
    topBtnRow->addWidget(settingsButton);
    topBtnRow->addWidget(logoutButton);
    topBar->addLayout(topBtnRow);


    // Tabs: 会话 / 通讯录
    leftTabs = new QTabWidget;
    leftTabs->addTab(conversationList, "会话");

    // Contacts tab with wrapper (count header + letter index)
    {
        auto *contactsTab = new QWidget;
        auto *contactsOuterLayout = new QVBoxLayout;
        contactsOuterLayout->setContentsMargins(0, 0, 0, 0);
        contactsOuterLayout->setSpacing(2);
        contactCountLabel = new QLabel("通讯录");
        contactCountLabel->setObjectName("sectionTitle");
        QMargins m = contactCountLabel->contentsMargins(); m.setLeft(8); contactCountLabel->setContentsMargins(m);
        contactsOuterLayout->addWidget(contactCountLabel);

        auto *contactsInnerLayout = new QHBoxLayout;
        contactsInnerLayout->setContentsMargins(0, 0, 0, 0);
        contactsInnerLayout->addWidget(contactList, 1);

        // Letter index sidebar
        auto *letterIndexWidget = new QWidget;
        letterIndexWidget->setFixedWidth(22);
        auto *letterIndexLayout = new QVBoxLayout;
        letterIndexLayout->setContentsMargins(0, 0, 2, 0);
        letterIndexLayout->setSpacing(0);
        const QString letters = QStringLiteral("ABCDEFGHIJKLMNOPQRSTUVWXYZ#");
        for (const QChar &ch : letters) {
            auto *btn = new QPushButton(QString(ch));
            btn->setFixedHeight(18);
            btn->setFlat(true);
            btn->setCursor(Qt::PointingHandCursor);
            btn->setStyleSheet("QPushButton { font-size:9px; padding:0; border:none; color:#666; } QPushButton:hover { color:#0066FF; font-weight:bold; }");
            connect(btn, &QPushButton::clicked, this, [this, ch]() {
                for (int i = 0; i < contactList->count(); ++i) {
                    QListWidgetItem *item = contactList->item(i);
                    const QString name = item->data(Qt::UserRole + 2).toString();
                    if (name.isEmpty()) continue;
                    const QChar first = name.at(0).toUpper();
                    if (first == ch || (ch == '#' && !first.isLetter())) {
                        contactList->scrollToItem(item, QAbstractItemView::PositionAtTop);
                        break;
                    }
                }
            });
            letterIndexLayout->addWidget(btn);
        }
        letterIndexWidget->setLayout(letterIndexLayout);
        contactsInnerLayout->addWidget(letterIndexWidget);

        contactsTab->setLayout(contactsOuterLayout);
        leftTabs->addTab(contactsTab, "通讯录");
    }

    auto *searchTab = new QWidget;
    auto *searchLayout = new QVBoxLayout;
    searchLayout->setContentsMargins(8, 8, 8, 8);
    searchLayout->setSpacing(6);
    searchLayout->addWidget(searchEdit);
    // Search category filter buttons
    auto *searchFilterRow = new QHBoxLayout;
    searchFilterRow->setSpacing(4);
    const QStringList searchFilterValues = {"all", "contact", "group", "message", "file"};
    for (int fi = 0; fi < 5; ++fi) {
        const QString labels[5] = {"全部", "联系人", "群聊", "消息", "文件"};
        auto *fb = new QPushButton(labels[fi]);
        fb->setCheckable(true);
        fb->setChecked(fi == 0);
        fb->setFlat(true);
        fb->setCursor(Qt::PointingHandCursor);
        fb->setStyleSheet("QPushButton{font-size:12px;padding:4px 10px;border:1px solid #cfd8e6;border-radius:12px;} QPushButton:checked{background:#2563eb;color:#fff;border-color:#2563eb;}");
        const QString fv = searchFilterValues[fi];
        const auto *filterBar = searchFilterRow; // copy for lambda
        connect(fb, &QPushButton::clicked, this, [this, fb, fv, filterBar]() {
            for (int j = 0; j < filterBar->count(); ++j) {
                QPushButton *b = qobject_cast<QPushButton*>(filterBar->itemAt(j)->widget());
                if (b) b->setChecked(false);
            }
            fb->setChecked(true);
            performSearch(fv);
        });
        searchFilterRow->addWidget(fb);
    }
    searchFilterRow->addStretch();
    searchLayout->addLayout(searchFilterRow);
    searchLayout->addWidget(searchResultList, 1);
    searchTab->setLayout(searchLayout);
    leftTabs->addTab(friendRequestList, "好友申请");
    leftTabs->addTab(searchTab, "搜索");

    fileList = new QListWidget;
    orgTree = new QTreeWidget;
    orgTree->setHeaderLabels(QStringList() << "名称" << "ID");
    orgTree->setColumnCount(2);
    orgTree->setRootIsDecorated(true);
    leftTabs->addTab(fileList, "文件");
    leftTabs->addTab(orgTree, "组织");

    sessionLayout->addLayout(topBar);
    sessionLayout->addWidget(leftTabs, 1);

    auto *sessionPanel = new QWidget;
    sessionPanel->setLayout(sessionLayout);

    // --- Connection settings panel (hidden by default) ---
    auto *configForm = new QFormLayout;
    configForm->setLabelAlignment(Qt::AlignLeft);
    configForm->setFormAlignment(Qt::AlignTop);
    configForm->setVerticalSpacing(8);
    configForm->addRow("服务器", hostEdit);
    configForm->addRow("HTTP 端口", httpPortEdit);
    configForm->addRow("TCP 端口", tcpPortEdit);
    auto *configPanel = new QWidget;
    configPanel->setLayout(configForm);
    configPanel->setVisible(false);

    // --- Left stack: login panel vs session panel ---
    leftStack = new QStackedWidget;
    loginPanel = new QWidget;
    loginPanel->setLayout(loginPanelOuter);
    leftStack->addWidget(loginPanel);
    leftStack->addWidget(sessionPanel);
    leftStack->addWidget(configPanel);

    // Settings button opens settings dialog
    connect(settingsButton, &QPushButton::clicked, this, &MainWindow::showSettingsDialog);

    auto *leftPanel = new QWidget;
    leftPanel->setObjectName("leftPanel");
    leftPanel->setMinimumWidth(330);
    leftPanel->setMaximumWidth(380);
    auto *leftPanelLayout = new QVBoxLayout;
    leftPanelLayout->setContentsMargins(0, 0, 0, 0);
    leftPanelLayout->addWidget(leftStack);
    leftPanel->setLayout(leftPanelLayout);

    // --- Right panel: chat area ---
    chatTitleLabel = new QLabel("选择一个会话开始聊天");
    chatTitleLabel->setObjectName("sectionTitle");
    callStatusLabel = new QLabel;
    callStatusLabel->setVisible(false);
    actionStatusLabel = new QLabel("连接认证后即可发送消息或发起语音。");
    actionStatusLabel->setWordWrap(true);
    actionStatusLabel->setObjectName("notice");

    // Top bar for chat header
    auto *chatHeader = new QHBoxLayout;
    chatHeader->setContentsMargins(0, 0, 0, 6);
    chatHeader->addWidget(chatTitleLabel);
    chatHeader->addStretch();
    auto *conversationSettingsButton = new QPushButton("会话设置");
    conversationSettingsButton->setObjectName("secondaryButton");
    connect(conversationSettingsButton, &QPushButton::clicked, this, &MainWindow::showConversationSettingsDialog);
    auto *topAudioButton = new QPushButton("语音");
    auto *topVideoButton = new QPushButton("视频");
    auto *topHangupButton = new QPushButton("挂断");
    topAudioButton->setObjectName("primaryButton");
    topVideoButton->setObjectName("primaryButton");
    topHangupButton->setObjectName("dangerButton");
    topAudioButton->setMinimumWidth(76);
    topVideoButton->setMinimumWidth(76);
    topHangupButton->setMinimumWidth(76);
    chatHeader->addWidget(conversationSettingsButton);
    chatHeader->addWidget(topAudioButton);
    chatHeader->addWidget(topVideoButton);
    chatHeader->addWidget(topHangupButton);
    chatHeader->addWidget(callStatusLabel);

    // --- Chat browser (QTextBrowser for rich HTML messages) ---
    chatBrowser = new QTextBrowser;
    chatBrowser->setObjectName("chatBrowser");
    chatBrowser->setOpenExternalLinks(false);
    chatBrowser->setReadOnly(true);
    connect(chatBrowser, &QTextBrowser::anchorClicked, this, [this](const QUrl &url) {
        if (url.scheme() == "msgop") {
            handleMessageActionLink(url);
            return;
        }
        if (url.isValid() && (url.scheme() == "http" || url.scheme() == "https")) {
            QDesktopServices::openUrl(url);
        }
    });

    // --- Composer ---
    messageEdit = new QLineEdit;
    messageEdit->setPlaceholderText("输入消息...");
    sendButton = new QPushButton("发送");
    sendButton->setObjectName("primaryButton");
    sendButton->setMinimumWidth(80);
    fileButton = new QPushButton("文件");
    fileButton->setMinimumWidth(60);
    voiceRecordButton = new QPushButton("语音");
    voiceRecordButton->setMinimumWidth(60);
    imageEditButton = new QPushButton("图片编辑");
    imageEditButton->setMinimumWidth(76);
    forwardSelectedButton = new QPushButton("批量转发");
    forwardSelectedButton->setMinimumWidth(76);
    cardButton = new QPushButton("名片");
    cardButton->setMinimumWidth(60);

    auto *composer = new QHBoxLayout;
    composer->setSpacing(10);
    messageEdit->setMinimumHeight(42);
    composer->addWidget(fileButton);
    composer->addWidget(voiceRecordButton);
    composer->addWidget(imageEditButton);
    composer->addWidget(forwardSelectedButton);
    composer->addWidget(cardButton);
    composer->addWidget(messageEdit, 1);
    composer->addWidget(sendButton);
    composerPanel = new QWidget;
    composerPanel->setLayout(composer);

    // --- Message operations ---
    favoriteButton = new QPushButton("收藏");
    likeButton = new QPushButton("点赞");
    recallButton = new QPushButton("撤回");
    editButton = new QPushButton("编辑");
    favoriteButton->setMinimumWidth(60);
    likeButton->setMinimumWidth(60);
    recallButton->setMinimumWidth(60);
    editButton->setMinimumWidth(60);

    auto *messageOps = new QHBoxLayout;
    messageOps->setSpacing(8);
    messageOps->addWidget(favoriteButton);
    messageOps->addWidget(likeButton);
    messageOps->addWidget(recallButton);
    messageOps->addWidget(editButton);
    messageOps->addStretch();

    // --- Call controls (hidden by default) ---
    connectButton = new QPushButton("连接");
    authButton = new QPushButton("登录认证");
    pingButton = new QPushButton("心跳");
    readinessButton = new QPushButton("检测通话");
    audioButton = new QPushButton("语音");
    videoButton = new QPushButton("视频");
    answerButton = new QPushButton("接听");
    rejectButton = new QPushButton("拒绝");
    hangupButton = new QPushButton("挂断");
    historyButton = new QPushButton("通话记录");
    debugToggleButton = new QPushButton("显示调试日志");

    readinessButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    audioButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    videoButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    answerButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    rejectButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    hangupButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);
    historyButton->setSizePolicy(QSizePolicy::Ignored, QSizePolicy::Fixed);

    auto *callButtons = new QGridLayout;
    callButtons->setSpacing(10);
    callButtons->addWidget(readinessButton, 0, 0);
    callButtons->addWidget(audioButton, 0, 1);
    callButtons->addWidget(videoButton, 1, 0);
    callButtons->addWidget(answerButton, 1, 1);
    callButtons->addWidget(rejectButton, 2, 0);
    callButtons->addWidget(hangupButton, 2, 1);
    callButtons->addWidget(historyButton, 3, 0, 1, 2);

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

    // --- Call screen (full overlay) ---
    callScreen = new QWidget;
    callScreen->setObjectName("callScreen");
    callScreen->setMinimumHeight(430);
    callScreen->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
    auto *callScreenLayout = new QVBoxLayout;
    callScreenLayout->setContentsMargins(28, 26, 28, 26);
    callScreenLayout->setSpacing(16);
    callScreenPeer = new QLabel;
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

    // --- Debug log ---
    eventView = new QTextEdit;
    eventView->setReadOnly(true);
    eventView->setVisible(debugVisible);

    // --- Assemble right layout ---
    auto *rightLayout = new QVBoxLayout;
    rightLayout->setContentsMargins(24, 20, 24, 18);
    rightLayout->setSpacing(10);
    rightLayout->addLayout(chatHeader);
    rightLayout->addWidget(actionStatusLabel);
    rightLayout->addWidget(callScreen, 1);
    rightLayout->addWidget(chatBrowser, 1);
    rightLayout->addWidget(composerPanel);
    rightLayout->addLayout(messageOps);
    rightLayout->addWidget(callPanel);
    rightLayout->addWidget(debugToggleButton);
    eventView->setMaximumHeight(180);
    rightLayout->addWidget(eventView);

    auto *rightPanel = new QWidget;
    rightPanel->setMinimumWidth(0);
    rightPanel->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Preferred);
    rightPanel->setLayout(rightLayout);

    // --- Splitter ---
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

    // --- Theme ---
    darkMode = settings.value("display/darkMode", false).toBool();
    applyTheme();

    // --- Signals ---
    connect(loginButton, &QPushButton::clicked, this, &MainWindow::doLogin);
    connect(logoutButton, &QPushButton::clicked, this, &MainWindow::doLogout);
    connect(connectButton, &QPushButton::clicked, this, &MainWindow::connectToServer);
    connect(topAudioButton, &QPushButton::clicked, this, &MainWindow::startAudioCall);
    connect(topVideoButton, &QPushButton::clicked, this, &MainWindow::startVideoCall);
    connect(topHangupButton, &QPushButton::clicked, this, &MainWindow::hangupCall);
    connect(authButton, &QPushButton::clicked, this, &MainWindow::sendAuth);
    connect(pingButton, &QPushButton::clicked, this, &MainWindow::sendPing);
    connect(sendButton, &QPushButton::clicked, this, &MainWindow::sendText);
    connect(fileButton, &QPushButton::clicked, this, &MainWindow::pickAndSendFile);
    connect(voiceRecordButton, &QPushButton::clicked, this, &MainWindow::toggleVoiceRecording);
    connect(imageEditButton, &QPushButton::clicked, this, &MainWindow::pickEditAndSendImage);
    connect(forwardSelectedButton, &QPushButton::clicked, this, &MainWindow::forwardSelectedMessages);
    connect(cardButton, &QPushButton::clicked, this, &MainWindow::sendCardMessage);
    connect(favoriteButton, &QPushButton::clicked, this, &MainWindow::favoriteLastMessage);
    connect(likeButton, &QPushButton::clicked, this, &MainWindow::likeLastMessage);
    connect(recallButton, &QPushButton::clicked, this, &MainWindow::recallLastMessage);
    connect(editButton, &QPushButton::clicked, this, &MainWindow::editLastMessage);
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
    sipMediaClient = new SipMediaClient(this);
    connect(sipMediaClient, &SipMediaClient::logLine, this, &MainWindow::appendLog);
    connect(conversationList, &QListWidget::itemClicked, this, &MainWindow::onConversationClicked);
    connect(contactList, &QListWidget::itemClicked, this, &MainWindow::onContactClicked);
    connect(contactList, &QListWidget::itemDoubleClicked, this, [this](QListWidgetItem *item) {
        if (item) showContactProfile(item->data(Qt::UserRole).toString());
    });
    connect(friendRequestList, &QListWidget::itemDoubleClicked, this, [this](QListWidgetItem *item) {
        if (!item) return;
        const QString requestId = item->data(Qt::UserRole).toString();
        if (requestId.isEmpty()) return;
        const int choice = QMessageBox::question(this, "好友申请", "接受这个好友申请吗？",
                                                 QMessageBox::Yes | QMessageBox::No | QMessageBox::Cancel);
        if (choice == QMessageBox::Yes) acceptFriendRequest(requestId);
        if (choice == QMessageBox::No) rejectFriendRequest(requestId);
    });
    connect(searchEdit, &QLineEdit::returnPressed, this, [this]() { performSearch("all"); });
    connect(searchResultList, &QListWidget::itemDoubleClicked, this, &MainWindow::onSearchResultClicked);
    connect(leftTabs, &QTabWidget::currentChanged, this, [this](int index) {
        const QString tab = leftTabs->tabText(index);
        if (tab.contains("好友")) loadFriendRequests();
        if (tab.contains("搜索") && !searchEdit->text().trimmed().isEmpty()) performSearch();
        if (tab.contains("文件") && fileList->count() == 0) loadFiles();
        if (tab.contains("组织") && orgTree->topLevelItemCount() == 0) loadOrganization();
    });
    connect(orgTree, &QTreeWidget::itemDoubleClicked, this, &MainWindow::onOrgTreeItemDoubleClicked);
    connect(&socket, &QTcpSocket::readyRead, this, &MainWindow::readSocket);
    connect(&socket, &QTcpSocket::connected, this, &MainWindow::updateConnectionState);
    connect(&socket, &QTcpSocket::disconnected, this, &MainWindow::updateConnectionState);
    connect(&socket, &QTcpSocket::disconnected, this, &MainWindow::scheduleReconnect);

    // Typing indicator
    typingTimer->setSingleShot(true);
    connect(typingTimer, &QTimer::timeout, this, &MainWindow::clearTypingIndicator);

    // Group online poll
    groupOnlineTimer->setInterval(30000);
    connect(groupOnlineTimer, &QTimer::timeout, this, &MainWindow::pollGroupOnlineStatus);
    connect(messageEdit, &QLineEdit::textChanged, this, [this](const QString &text) {
        if (!text.isEmpty()) {
            sendTyping(true);
            typingTimer->start(3000);
        }
    });

    connect(reconnectTimer, &QTimer::timeout, this, [this]() {
        if (loggedIn && !closing && socket.state() == QAbstractSocket::UnconnectedState) {
            connectToServer();
        }
    });
    connect(&socket, static_cast<void (QTcpSocket::*)(QAbstractSocket::SocketError)>(&QTcpSocket::error),
            this, [this]() {
                setActionStatus("连接失败：" + socket.errorString(), true);
                appendLog("连接失败：" + socket.errorString());
            });

    initLocalStore();
    loadSettings();

    // Show login or session panel
    if (!tokenEdit->text().isEmpty() && !userIdEdit->text().isEmpty()) {
        loggedIn = true;
        showLoggedInUI();
        connectToServer();
    } else {
        showLoginUI();
    }

    updateConnectionState();
    refreshCallControls();
}

void MainWindow::closeEvent(QCloseEvent *event)
{
    closing = true;
    reconnectTimer->stop();
    saveSettings();
    sipMediaClient->stop();
#ifdef Q_OS_WIN
    if (voiceRecordCtx) {
        VoiceRecordCtx *ctx = reinterpret_cast<VoiceRecordCtx *>(voiceRecordCtx);
        if (ctx->hWaveIn) {
            waveInReset(ctx->hWaveIn);
            waveInClose(ctx->hWaveIn);
        }
        delete ctx->file;
        delete ctx;
        voiceRecordCtx = nullptr;
    }
#endif
    if (socket.state() == QAbstractSocket::ConnectedState) {
        socket.disconnectFromHost();
    }
    event->accept();
}

// ---------------------------------------------------------------------------
// Settings persistence
// ---------------------------------------------------------------------------

void MainWindow::loadSettings()
{
    hostEdit->setText(settings.value("server/host", "127.0.0.1").toString());
    httpPortEdit->setText(settings.value("server/httpPort", "18080").toString());
    tcpPortEdit->setText(settings.value("server/tcpPort", "19090").toString());
    userIdEdit->setText(settings.value("auth/userId").toString());
    tokenEdit->setText(settings.value("auth/token").toString());
    phoneEdit->setText(settings.value("auth/phone").toString());
    const QByteArray geo = settings.value("window/geometry").toByteArray();
    if (!geo.isEmpty()) {
        restoreGeometry(geo);
    }
    currentConversationId = settings.value("chat/lastConversation").toString();
}

void MainWindow::saveSettings()
{
    settings.setValue("server/host", hostEdit->text().trimmed());
    settings.setValue("server/httpPort", httpPortEdit->text().trimmed());
    settings.setValue("server/tcpPort", tcpPortEdit->text().trimmed());
    settings.setValue("auth/userId", userIdEdit->text().trimmed());
    settings.setValue("auth/token", tokenEdit->text().trimmed());
    settings.setValue("auth/phone", phoneEdit->text().trimmed());
    settings.setValue("window/geometry", saveGeometry());
    if (!currentConversationId.isEmpty()) {
        settings.setValue("chat/lastConversation", currentConversationId);
    }
}

// ---------------------------------------------------------------------------
// Login / Logout
// ---------------------------------------------------------------------------

void MainWindow::doLogin()
{
    const QString phone = phoneEdit->text().trimmed();
    const QString pwd = passwordEdit->text().trimmed();
    if (phone.isEmpty() || pwd.isEmpty()) {
        loginStatusLabel->setText("请输入手机号和密码");
        loginStatusLabel->setVisible(true);
        return;
    }
    loginButton->setEnabled(false);
    loginStatusLabel->setText("正在登录...");
    loginStatusLabel->setVisible(true);

    QJsonObject body;
    body.insert("phone", phone);
    body.insert("password", pwd);

    // Login uses a direct request (no auth header yet)
    const QUrl url(baseUrl() + "/api/auth/login");
    QNetworkRequest request(url);
    request.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    QNetworkReply *reply = http.post(request, QJsonDocument(body).toJson(QJsonDocument::Compact));
    connect(reply, &QNetworkReply::finished, this, [this, reply]() {
        const QByteArray data = reply->readAll();
        loginButton->setEnabled(true);
        if (reply->error() != QNetworkReply::NoError) {
            loginStatusLabel->setText("网络错误：" + reply->errorString());
            loginStatusLabel->setVisible(true);
            appendLog("登录请求失败：" + reply->errorString());
        } else {
            handleLoginResponse(data);
        }
        reply->deleteLater();
    });
}

void MainWindow::handleLoginResponse(const QByteArray &body)
{
    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject()) {
        loginStatusLabel->setText("服务端返回格式错误");
        return;
    }
    const QJsonObject root = doc.object();
    if (!root.value("success").toBool()) {
        loginStatusLabel->setText("登录失败：" + root.value("error").toString());
        return;
    }
    const QJsonObject data = root.value("data").toObject();
    const QString token = data.value("token").toString();
    const QJsonObject user = data.value("user").toObject();
    QString userId = data.value("userId").toString();
    QString userName = data.value("displayName").toString(userId);
    if (userId.isEmpty()) {
        userId = user.value("id").toString(user.value("userId").toString());
    }
    if (userName.isEmpty() || userName == userId) {
        userName = user.value("name").toString(user.value("displayName").toString(userId));
    }

    if (token.isEmpty() || userId.isEmpty()) {
        loginStatusLabel->setText("登录返回数据不完整");
        return;
    }

    tokenEdit->setText(token);
    userIdEdit->setText(userId);
    loggedIn = true;
    settings.setValue("app/firstLaunch", false);
    saveSettings();
    showLoggedInUI();
    connectToServer();
    setActionStatus("登录成功，欢迎 " + userName);
    appendLog("登录成功 userId=" + userId);
}

void MainWindow::doLogout()
{
    loggedIn = false;
    reconnectTimer->stop();
    tokenEdit->clear();
    userIdEdit->clear();
    conversations.clear();
    contacts.clear();
    conversationList->clear();
    contactList->clear();
    friendRequestList->clear();
    searchResultList->clear();
    chatBrowser->clear();
    if (socket.state() == QAbstractSocket::ConnectedState) {
        socket.disconnectFromHost();
    }
    settings.remove("auth/token");
    settings.remove("auth/userId");
    showLoginUI();
    setActionStatus("已退出登录");
}

void MainWindow::showLoginUI()
{
    leftStack->setCurrentWidget(loginPanel);
    chatTitleLabel->setText("请先登录");
    composerPanel->setVisible(false);
}

void MainWindow::showLoggedInUI()
{
    leftStack->setCurrentIndex(1); // session panel
    composerPanel->setVisible(true);
    loadConversations();
    loadContacts();
    loadFriendRequests();
    resumeQueuedUploads();
}

// ---------------------------------------------------------------------------
// Dynamic session list
// ---------------------------------------------------------------------------

void MainWindow::loadConversations()
{
    apiGet("/api/conversations");
}

void MainWindow::handleConversationsResponse(const QByteArray &body)
{
    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject()) return;
    const QJsonObject root = doc.object();
    if (!root.value("success").toBool()) return;

    const QString label;
    if (label.startsWith("POST /api/messages/") || label.startsWith("PATCH /api/messages/")) {
        setActionStatus("消息操作已同步到服务端。");
        if (!currentConversationId.isEmpty()) {
            loadMessages(currentConversationId);
        }
        return;
    }

    if (label.startsWith("POST /api/messages/") || label.startsWith("PATCH /api/messages/")) {
        setActionStatus("消息操作已同步到服务端。");
        if (!currentConversationId.isEmpty()) {
            loadMessages(currentConversationId);
        }
        return;
    }

    const QJsonValue data = root.value("data");
    QJsonArray items;
    if (data.isArray()) {
        items = data.toArray();
    } else if (data.isObject()) {
        // Some APIs wrap in {conversations: [...]}
        const QJsonObject dataObj = data.toObject();
        if (dataObj.contains("conversations")) {
            items = dataObj.value("conversations").toArray();
        }
    }

    conversations.clear();
    conversationList->clear();

    for (int i = 0; i < items.size(); ++i) {
        const QJsonObject obj = items.at(i).toObject();
        ConversationItem ci;
        ci.id = obj.value("id").toString();
        ci.name = obj.value("name").toString(obj.value("title").toString("未知会话"));
        ci.type = obj.value("type").toString(isGroupConversation(ci.id) ? "group" : "single");
        ci.targetId = obj.value("targetId").toString();
        if (!obj.contains("name") && !obj.contains("title")) {
            ci.name.clear();
        }
        if (ci.name.isEmpty() || ci.name.contains("?")) {
            ci.name = ci.targetId.isEmpty() ? ci.id : ci.targetId;
        }
        const QString lastType = obj.value("lastType").toString(obj.value("messageType").toString("text"));
        const QString rawLast = obj.value("lastMessage").toString(obj.value("lastMessageContent").toString(obj.value("lastContent").toString()));
        ci.lastMessage = readableMessageContent(lastType, rawLast);
        ci.timestamp = obj.value("updatedAt").toString(obj.value("lastMessageTime").toString());
        ci.unreadCount = obj.value("unreadCount").toInt(0);
        ci.muted = obj.value("muted").toBool(false);
        ci.pinned = obj.value("pinned").toBool(false);
        ci.screenshotNotice = obj.value("screenshotNotice").toBool(true);
        ci.recallNotice = obj.value("recallNotice").toBool(true);
        ci.readAfterBurn = obj.value("readAfterBurn").toBool(false);
        ci.strongReminder = obj.value("strongReminder").toBool(false);
        ci.displayMemberNicknames = obj.value("displayMemberNicknames").toBool(true);
        ci.savedToContacts = obj.value("savedToContacts").toBool(false);
        if (ci.id.isEmpty()) continue;
        conversations.append(ci);

        // Build display text
        const QString timeStr = smartTimeFormat(ci.timestamp);
        QString display = ci.name;
        if (ci.pinned) display = "★ " + display;
        // Online indicator (use green/gray dot)
        const QString onlineDot = ci.type == "single" ? QString::fromUtf8("● ") : QString();
        // Group conversation prefix
        if (isGroupConversation(ci.id)) {
            display = QString::fromUtf8("\xf0\x9f\x91\xa5 ") + display;
        }
        if (!ci.lastMessage.isEmpty()) {
            display += "\n" + ci.lastMessage.left(40);
        }
        QString metaLine;
        if (ci.unreadCount > 0) {
            metaLine += ci.unreadCount > 99 ? " [99+]" : (" [" + QString::number(ci.unreadCount) + "]");
        }
        if (!timeStr.isEmpty()) {
            metaLine += " " + timeStr;
        }
        if (ci.muted) {
            display += " (免打扰)";
            metaLine += " 静音";
        }
        if (!metaLine.isEmpty()) {
            display += "\n" + metaLine;
        }

        auto *item = new QListWidgetItem(display);
        item->setData(Qt::UserRole, ci.id);
        item->setData(Qt::UserRole + 1, ci.muted);
        conversationList->addItem(item);

        // Style: gray if muted, bold if unread
        if (ci.muted) {
            QFont f = item->font();
            QColor gray("#9aa7b8");
            item->setForeground(gray);
            item->setFont(f);
        } else if (ci.unreadCount > 0) {
            QFont f = item->font();
            f.setBold(true);
            item->setFont(f);
        }
    }

    // Restore last conversation selection
    if (!currentConversationId.isEmpty()) {
        for (int i = 0; i < conversationList->count(); ++i) {
            if (conversationList->item(i)->data(Qt::UserRole).toString() == currentConversationId) {
                conversationList->setCurrentRow(i);
                onConversationClicked(conversationList->item(i));
                break;
            }
        }
    } else if (conversationList->count() > 0) {
        conversationList->setCurrentRow(0);
        onConversationClicked(conversationList->item(0));
    }

    appendLog("已加载会话列表：" + QString::number(conversations.size()) + " 条");
}

void MainWindow::onConversationContextMenu(const QPoint &pos)
{
    QListWidgetItem *item = conversationList->itemAt(pos);
    if (!item) return;
    const QString convId = item->data(Qt::UserRole).toString();
    ConversationItem *ci = nullptr;
    for (int i = 0; i < conversations.size(); ++i) {
        if (conversations[i].id == convId) { ci = &conversations[i]; break; }
    }
    if (!ci) return;

    QMenu menu(this);
    QAction *muteAction = menu.addAction(ci->muted ? "开启通知" : "消息免打扰");
    QAction *pinAction = menu.addAction(ci->pinned ? "取消置顶" : "置顶会话");
    menu.addSeparator();
    QAction *deleteAction = menu.addAction("删除会话");
    deleteAction->setIcon(QIcon()); // no icon needed

    QAction *chosen = menu.exec(conversationList->mapToGlobal(pos));
    if (!chosen) return;

    if (chosen == muteAction) {
        ci->muted = !ci->muted;
        QJsonObject body;
        body.insert("muted", ci->muted);
        apiPatch("/api/conversations/" + QUrl::toPercentEncoding(ci->id) + "/settings", body);
        loadConversations();
    } else if (chosen == pinAction) {
        ci->pinned = !ci->pinned;
        QJsonObject body;
        body.insert("pinned", ci->pinned);
        apiPatch("/api/conversations/" + QUrl::toPercentEncoding(ci->id) + "/settings", body);
        loadConversations();
    } else if (chosen == deleteAction) {
        if (QMessageBox::question(this, "删除会话", "确定删除该会话吗？") == QMessageBox::Yes) {
            QNetworkRequest req(QUrl(baseUrl() + "/api/conversations/" + QUrl::toPercentEncoding(ci->id)));
            req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
            const QString token = tokenEdit->text().trimmed();
            if (!token.isEmpty()) req.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
            QNetworkReply *reply = http.deleteResource(req);
            connect(reply, &QNetworkReply::finished, this, [this, reply]() {
                reply->deleteLater();
                loadConversations();
            });
        }
    }
}

void MainWindow::onConversationClicked(QListWidgetItem *item)
{
    if (!item) return;
    const QString convId = item->data(Qt::UserRole).toString();
    if (convId.isEmpty()) return;

    currentConversationId = convId;
    conversationIdEdit->setText(convId);

    // Find conversation name and peer id. Single-chat calls must target the
    // selected conversation peer; a stale peerId can make /api/calls fail.
    QString convName = convId;
    QString targetId;
    for (int i = 0; i < conversations.size(); ++i) {
        if (conversations.at(i).id == convId) {
            convName = conversations.at(i).name;
            targetId = conversations.at(i).targetId;
            break;
        }
    }

    if (!targetId.isEmpty() && targetId != userIdEdit->text().trimmed()) {
        peerIdEdit->setText(targetId);
    }

    chatTitleLabel->setText(convName);
    chatBrowser->clear();
    saveSettings();
    loadMessages(convId);
    setActionStatus("已切换到会话：" + convName);

    // Start/stop group online polling
    bool isGroup = false;
    for (int i = 0; i < conversations.size(); ++i) {
        if (conversations.at(i).id == convId && conversations.at(i).type == "group") {
            isGroup = true;
            break;
        }
    }
    if (isGroup) {
        pollGroupOnlineStatus();
        groupOnlineTimer->start();
    } else {
        groupOnlineTimer->stop();
    }
}

// ---------------------------------------------------------------------------
// Contacts
// ---------------------------------------------------------------------------

void MainWindow::loadContacts()
{
    // Load friends first; directory users as fallback
    QUrlQuery query;
    query.addQueryItem("userId", userIdEdit->text().trimmed());
    apiGet("/api/friends?" + query.toString());
}

void MainWindow::handleContactsResponse(const QByteArray &body)
{
    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject()) return;
    const QJsonObject root = doc.object();
    if (!root.value("success").toBool()) return;

    const QJsonValue data = root.value("data");
    QJsonArray items;
    if (data.isArray()) {
        items = data.toArray();
    } else if (data.isObject()) {
        const QJsonObject dataObj = data.toObject();
        // Try common keys
        if (dataObj.contains("friends")) {
            items = dataObj.value("friends").toArray();
        } else if (dataObj.contains("users")) {
            items = dataObj.value("users").toArray();
        }
    }

    contacts.clear();
    contactList->clear();

    for (int i = 0; i < items.size(); ++i) {
        const QJsonObject obj = items.at(i).toObject();
        ContactItem ci;
        ci.id = obj.value("id").toString(obj.value("userId").toString());
        ci.name = obj.value("name").toString(obj.value("nickname").toString(ci.id));
        ci.online = obj.value("online").toBool(obj.value("isOnline").toBool());
        if (ci.id.isEmpty() || ci.id == userIdEdit->text()) continue;
        contacts.append(ci);

        QString display = ci.name;
        if (ci.online) {
            display += "  [在线]";
        } else {
            // Show offline duration if lastSeen is available
            const QString lastSeen = obj.value("lastSeen").toString(obj.value("lastOnlineAt").toString());
            if (!lastSeen.isEmpty()) {
                const QDateTime lastDt = QDateTime::fromString(lastSeen, Qt::ISODate);
                if (lastDt.isValid()) {
                    const qint64 secs = lastDt.secsTo(QDateTime::currentDateTimeUtc());
                    if (secs < 60)
                        display += "  [离线]";
                    else if (secs < 3600)
                        display += QString("  [离线%1分钟]").arg(secs / 60);
                    else if (secs < 86400)
                        display += QString("  [离线%1小时]").arg(secs / 3600);
                    else
                        display += QString("  [离线%1天]").arg(secs / 86400);
                } else {
                    display += "  [离线]";
                }
            } else {
                display += "  [离线]";
            }
        }
        auto *item = new QListWidgetItem(display);
        item->setData(Qt::UserRole, ci.id);
        item->setData(Qt::UserRole + 2, ci.name); // original name for letter index
        contactList->addItem(item);
    }

    contactCountLabel->setText(QString("通讯录 — 共%1位联系人").arg(contacts.size()));
    appendLog("已加载通讯录：" + QString::number(contacts.size()) + " 人");
}

void MainWindow::onContactClicked(QListWidgetItem *item)
{
    if (!item) return;
    const QString uid = item->data(Qt::UserRole).toString();
    if (uid.isEmpty()) return;
    peerIdEdit->setText(uid);
    setActionStatus("已选择联系人：" + uid + "，可发起通话或发送消息。");
}

// ---------------------------------------------------------------------------
// Message loading and chat display
// ---------------------------------------------------------------------------

void MainWindow::loadMessages(const QString &conversationId)
{
    apiGet("/api/conversations/" + conversationId + "/messages");
}

void MainWindow::handleMessagesResponse(const QString &conversationId, const QByteArray &body)
{
    // Only apply if still viewing same conversation
    if (conversationId != currentConversationId) return;

    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject()) return;
    const QJsonObject root = doc.object();
    if (!root.value("success").toBool()) return;

    const QJsonValue data = root.value("data");
    QJsonArray items;
    if (data.isArray()) {
        items = data.toArray();
    } else if (data.isObject()) {
        const QJsonObject dataObj = data.toObject();
        if (dataObj.contains("messages")) {
            items = dataObj.value("messages").toArray();
        }
    }

    chatBrowser->clear();
    const QString fontSizeSetting = settings.value("chat/fontSize", "medium").toString();
    int bubbleFontSize = 15;
    if (fontSizeSetting == "small") bubbleFontSize = 13;
    else if (fontSizeSetting == "large") bubbleFontSize = 18;
    const int senderFontSize = bubbleFontSize - 3;
    const int timeFontSize = bubbleFontSize - 4;
    chatBrowser->document()->setDefaultStyleSheet(
        "body { margin: 0; padding: 0; }"
        ".msg-row { margin-bottom: 10px; overflow: hidden; }"
        ".msg-left { text-align: left; }"
        ".msg-right { text-align: right; }"
        ".msg-bubble { display: inline; padding: 10px 14px; border-radius: 12px; text-align: left; font-size: "
        + QString::number(bubbleFontSize) + "px; line-height: 1.4; word-wrap: break-word; }"
        ".bubble-left { background: #ffffff; border: 1px solid #e2e8f0; border-top-left-radius: 4px; }"
        ".bubble-right { background: #2563eb; color: #ffffff; border-top-right-radius: 4px; }"
        ".msg-sender { font-size: " + QString::number(senderFontSize) + "px; color: #64748b; margin-bottom: 3px; }"
        ".msg-time { font-size: " + QString::number(timeFontSize) + "px; color: #94a3b8; margin-top: 3px; }"
        ".msg-time-right { font-size: " + QString::number(timeFontSize) + "px; color: #bfdbfe; margin-top: 3px; }"
        ".msg-ops { font-size: " + QString::number(timeFontSize) + "px; margin-top: 4px; }"
        ".msg-ops a { color: #2563eb; text-decoration: none; margin-right: 8px; }"
        ".msg-right .msg-ops a { color: #bfdbfe; }"
        ".avatar { display: inline-block; width: 32px; height: 32px; border-radius: 50%; color: #ffffff; font-size: 14px; font-weight: 700; text-align: center; line-height: 32px; vertical-align: top; margin-right: 8px; }"
        ".system-msg { text-align: center; color: #94a3b8; font-size: 13px; margin: 8px 0; }");

    // Build HTML
    const bool isGroup = isGroupConversation(conversationId);
    QString html;
    messageContentById.clear();
    for (int i = 0; i < items.size(); ++i) {
        const QJsonObject obj = items.at(i).toObject();
        const QString messageId = obj.value("id").toString(obj.value("messageId").toString());
        const QString sender = obj.value("senderId").toString(obj.value("from").toString(obj.value("sender").toString()));
        const QString senderName = obj.value("senderName").toString(sender);
        const QString content = obj.value("content").toString(obj.value("text").toString());
        const QString ts = obj.value("createdAt").toString(obj.value("timestamp").toString());
        const QString msgType = obj.value("type").toString(obj.value("messageType").toString());
        const QString fileUrl = obj.value("fileUrl").toString(obj.value("url").toString(obj.value("path").toString()));
        const QString fileName = obj.value("fileName").toString(obj.value("name").toString(obj.value("filename").toString()));
        const qint64 fileSize = static_cast<qint64>(obj.value("fileSize").toDouble(obj.value("size").toDouble(0)));

        if (msgType == "system" || sender == "system") {
            // Detect system message sub-type for special icon/color
            const QString lower = content.toLower();
            QString icon = QString::fromUtf8("\xe2\x84\xb9"); // default info
            QString sysColor = "#94a3b8";
            if (lower.contains("create") || lower.contains("创建")) {
                icon = QString::fromUtf8("\xf0\x9f\x86\x95"); sysColor = "#16a34a";
            } else if (lower.contains("add") || lower.contains("加入") || lower.contains("邀请")) {
                icon = QString::fromUtf8("\xe2\x9e\x95"); sysColor = "#2563eb";
            } else if (lower.contains("remove") || lower.contains("移除") || lower.contains("踢出")) {
                icon = QString::fromUtf8("\xe2\x9e\x96"); sysColor = "#dc2626";
            } else if (lower.contains("name") || lower.contains("改名")) {
                icon = QString::fromUtf8("\xe2\x9c\x8f"); sysColor = "#d97706";
            } else if (lower.contains("notice") || lower.contains("公告")) {
                icon = QString::fromUtf8("\xf0\x9f\x93\xa2"); sysColor = "#7c3aed";
            }
            html += "<div class='system-msg' style='color:" + sysColor + ";'>"
                  + icon + " " + content.toHtmlEscaped() + "</div>";
            continue;
        }
        if (!messageId.isEmpty()) {
            messageContentById.insert(messageId, content);
            lastMessageId = messageId;
        }

        const bool isSelf = (sender == userIdEdit->text());
        const QString alignClass = isSelf ? "msg-right" : "msg-left";
        const QString bubbleClass = isSelf ? "bubble-right" : "bubble-left";
        const QString timeClass = isSelf ? "msg-time-right" : "msg-time";

        // In group chats, always show sender name
        const bool showSender = !isSelf || isGroup;
        const QString displaySenderName = isSelf ? QString::fromUtf8("\xe6\x88\x91") : senderName;

        QString timeStr;
        if (!ts.isEmpty()) {
            QDateTime dt = QDateTime::fromString(ts, Qt::ISODate);
            if (dt.isValid()) {
                timeStr = dt.toString("HH:mm");
            } else {
                timeStr = ts.left(16);
            }
        }

        html += "<div class='msg-row " + alignClass + "'>";
        if (!isSelf) {
            const QString letter = avatarLetter(senderName);
            const QString color = avatarColor(sender);
            html += "<span class='avatar' style='background:" + color + "'>" + letter + "</span>";
        }
        html += "<div>";
        if (showSender) {
            html += "<div class='msg-sender'>" + displaySenderName.toHtmlEscaped() + "</div>";
        }

        // @highlight: detect @current user in content
        bool atHighlight = false;
        const QString myId = userIdEdit->text().trimmed();
        if (isGroup && content.contains("@" + myId)) {
            atHighlight = true;
        }

        // Render content based on message type
        if (msgType == "image" && !fileUrl.isEmpty()) {
            const QString fullUrl = resolveFileUrl(fileUrl);
            const QString encodedUrl = QString::fromUtf8(QUrl::toPercentEncoding(fullUrl));
            html += "<span class='" + bubbleClass + "'>"
                  + "<a href='msgop://view-image/" + encodedUrl + "' style='text-decoration:none;'>"
                  + "<img src='" + fullUrl
                  + "' style='max-width:200px;max-height:200px;border-radius:8px;cursor:pointer;'/>"
                  + "</a></span>";
        } else if (msgType == "file" && !fileUrl.isEmpty()) {
            const QString fullUrl = resolveFileUrl(fileUrl);
            const QString display = fileName.isEmpty() ? content : fileName;
            QString sizeStr;
            if (fileSize > 0) {
                if (fileSize >= 1048576) {
                    sizeStr = QString::number(fileSize / 1048576.0, 'f', 1) + " MB";
                } else if (fileSize >= 1024) {
                    sizeStr = QString::number(fileSize / 1024.0, 'f', 1) + " KB";
                } else {
                    sizeStr = QString::number(fileSize) + " B";
                }
            }
            html += "<span class='" + bubbleClass + "' style='cursor:pointer;'>"
                  + "<a href='" + fullUrl + "' style='text-decoration:none;color:inherit;'>"
                  + QString::fromUtf8("\xf0\x9f\x93\x84 ") + display.toHtmlEscaped();
            if (!sizeStr.isEmpty()) {
                html += "<br><span style='font-size:12px;opacity:0.7;'>" + sizeStr + "</span>";
            }
            html += "</a></span>";
        } else if (msgType == "voice" && !fileUrl.isEmpty()) {
            const QString fullUrl = resolveFileUrl(fileUrl);
            const QString encodedUrl = QString::fromUtf8(QUrl::toPercentEncoding(fullUrl));
            html += "<span class='" + bubbleClass + "' style='cursor:pointer;'>"
                  + "<a href='msgop://play-voice/" + encodedUrl + "' style='text-decoration:none;color:inherit;'>"
                  + QString::fromUtf8("\xf0\x9f\x94\x8a 语音消息 [点击播放]")
                  + "</a></span>";
        } else if (msgType == "video" && !fileUrl.isEmpty()) {
            const QString fullUrl = resolveFileUrl(fileUrl);
            const QString encodedUrl = QString::fromUtf8(QUrl::toPercentEncoding(fullUrl));
            html += "<span class='" + bubbleClass + "' style='cursor:pointer;'>"
                  + "<a href='msgop://play-voice/" + encodedUrl + "' style='text-decoration:none;color:inherit;'>"
                  + QString::fromUtf8("\xf0\x9f\x8e\xac 视频消息 [点击播放]")
                  + "</a></span>";
        } else {
            const QString displayContent = readableMessageContent(msgType, content);
            QString innerBubble;
            if (atHighlight) {
                innerBubble = "<span class='" + bubbleClass + "' style='background:#fef3c7;color:#92400e;border:1px solid #fcd34d;'>"
                           + autoLinkUrls(displayContent).replace("\n", "<br>") + "</span>";
            } else {
                innerBubble = "<span class='" + bubbleClass + "'>" + autoLinkUrls(displayContent).replace("\n", "<br>") + "</span>";
            }
            html += innerBubble;
        }

        if (!timeStr.isEmpty()) {
            html += "<div class='" + timeClass + "'>" + timeStr + "</div>";
        }
        if (!messageId.isEmpty()) {
            const QString encodedId = QString::fromUtf8(QUrl::toPercentEncoding(messageId));
            html += QString("<div class='msg-ops'>")
                  + "<a href='msgop://select/" + encodedId + "'>选择</a>"
                  + "<a href='msgop://forward/" + encodedId + "'>转发</a>"
                  + "<a href='msgop://readstatus/" + encodedId + "'>已读</a>"
                  + "<a href='msgop://favorite/" + encodedId + "'>收藏</a>"
                  + "<a href='msgop://like/" + encodedId + "'>点赞</a>";
            if (isSelf) {
                html += "<a href='msgop://recall/" + encodedId + "'>撤回</a>"
                      + "<a href='msgop://edit/" + encodedId + "'>编辑</a>";
            }
            html += "<a href='msgop://copy/" + encodedId + "'>复制</a>";
            html += "</div>";
        }
        html += "</div></div>";
    }

    chatBrowser->setHtml("<body style='padding:8px;'>" + html + "</body>");
    // Scroll to bottom
    QTimer::singleShot(50, this, [this]() {
        QTextCursor cursor = chatBrowser->textCursor();
        cursor.movePosition(QTextCursor::End);
        chatBrowser->setTextCursor(cursor);
    });

    appendLog("已加载消息：" + QString::number(items.size()) + " 条，会话=" + conversationId);
}

void MainWindow::appendChatBubble(const QString &sender, const QString &content, const QString &timestamp, bool isSelf)
{
    const bool isGroup = isGroupConversation(currentConversationId);
    const QString alignClass = isSelf ? "msg-right" : "msg-left";
    const QString bubbleClass = isSelf ? "bubble-right" : "bubble-left";
    const QString timeClass = isSelf ? "msg-time-right" : "msg-time";

    QString timeStr;
    QDateTime dt;
    if (!timestamp.isEmpty()) {
        dt = QDateTime::fromString(timestamp, Qt::ISODate);
    }
    if (!dt.isValid()) {
        dt = QDateTime::currentDateTime();
    }
    timeStr = dt.toString("HH:mm");

    QString senderName = sender;
    for (int i = 0; i < contacts.size(); ++i) {
        if (contacts.at(i).id == sender) {
            senderName = contacts.at(i).name;
            break;
        }
    }
    if (isSelf) senderName = QString::fromUtf8("\xe6\x88\x91");

    const bool showSender = !isSelf || isGroup;

    QString html = "<div class='msg-row " + alignClass + "'>";
    if (!isSelf) {
        const QString letter = avatarLetter(senderName);
        const QString color = avatarColor(sender);
        html += "<span class='avatar' style='background:" + color + "'>" + letter + "</span>";
    }
    html += "<div>";
    if (showSender) {
        html += "<div class='msg-sender'>" + senderName.toHtmlEscaped() + "</div>";
    }
    html += "<span class='" + bubbleClass + "'>" + autoLinkUrls(content).replace("\n", "<br>") + "</span>";
    html += "<div class='" + timeClass + "'>" + timeStr + "</div>";
    // Copy link
    const QString copyId = QUuid::createUuid().toString().remove('{').remove('}');
    messageContentById.insert(copyId, content);
    html += "<div class='msg-ops'><a href='msgop://copy/" + copyId + "'>复制</a></div>";
    html += "</div></div>";

    chatBrowser->append(html);
    QTimer::singleShot(50, this, [this]() {
        QTextCursor cursor = chatBrowser->textCursor();
        cursor.movePosition(QTextCursor::End);
        chatBrowser->setTextCursor(cursor);
    });
}

void MainWindow::appendChatBubble(const QString &sender, const QString &content, const QString &timestamp,
                                   bool isSelf, const QString &msgType, const QString &fileUrl,
                                   const QString &fileName, qint64 fileSize)
{
    // Fall back to text-only bubble for plain text or empty type
    if (msgType.isEmpty() || msgType == "text") {
        appendChatBubble(sender, content, timestamp, isSelf);
        return;
    }

    const bool isGroup = isGroupConversation(currentConversationId);
    const QString alignClass = isSelf ? "msg-right" : "msg-left";
    const QString bubbleClass = isSelf ? "bubble-right" : "bubble-left";
    const QString timeClass = isSelf ? "msg-time-right" : "msg-time";

    QString timeStr;
    QDateTime dt;
    if (!timestamp.isEmpty()) {
        dt = QDateTime::fromString(timestamp, Qt::ISODate);
    }
    if (!dt.isValid()) {
        dt = QDateTime::currentDateTime();
    }
    timeStr = dt.toString("HH:mm");

    QString senderName = sender;
    for (int i = 0; i < contacts.size(); ++i) {
        if (contacts.at(i).id == sender) {
            senderName = contacts.at(i).name;
            break;
        }
    }
    if (isSelf) senderName = QString::fromUtf8("\xe6\x88\x91");

    const bool showSender = !isSelf || isGroup;

    QString html = "<div class='msg-row " + alignClass + "'>";
    if (!isSelf) {
        const QString letter = avatarLetter(senderName);
        const QString color = avatarColor(sender);
        html += "<span class='avatar' style='background:" + color + "'>" + letter + "</span>";
    }
    html += "<div>";
    if (showSender) {
        html += "<div class='msg-sender'>" + senderName.toHtmlEscaped() + "</div>";
    }

    if (msgType == "image" && !fileUrl.isEmpty()) {
        const QString fullUrl = resolveFileUrl(fileUrl);
        html += "<span class='" + bubbleClass + "'>"
              + "<a href='" + fullUrl + "'><img src='" + fullUrl
              + "' style='max-width:200px;max-height:200px;border-radius:8px;cursor:pointer;'/></a>"
              + "</span>";
    } else if (msgType == "file" && !fileUrl.isEmpty()) {
        const QString fullUrl = resolveFileUrl(fileUrl);
        const QString display = fileName.isEmpty() ? content : fileName;
        QString sizeStr;
        if (fileSize > 0) {
            if (fileSize >= 1048576) {
                sizeStr = QString::number(fileSize / 1048576.0, 'f', 1) + " MB";
            } else if (fileSize >= 1024) {
                sizeStr = QString::number(fileSize / 1024.0, 'f', 1) + " KB";
            } else {
                sizeStr = QString::number(fileSize) + " B";
            }
        }
        html += "<span class='" + bubbleClass + "' style='cursor:pointer;'>"
              + "<a href='" + fullUrl + "' style='text-decoration:none;color:inherit;'>"
              + QString::fromUtf8("\xf0\x9f\x93\x84 ") + display.toHtmlEscaped();
        if (!sizeStr.isEmpty()) {
            html += "<br><span style='font-size:12px;opacity:0.7;'>" + sizeStr + "</span>";
        }
        html += "</a></span>";
    } else if (msgType == "voice" && !fileUrl.isEmpty()) {
        const QString fullUrl = resolveFileUrl(fileUrl);
        const QString encodedUrl = QString::fromUtf8(QUrl::toPercentEncoding(fullUrl));
        html += "<span class='" + bubbleClass + "' style='cursor:pointer;'>"
              + "<a href='msgop://play-voice/" + encodedUrl + "' style='text-decoration:none;color:inherit;'>"
              + QString::fromUtf8("\xf0\x9f\x94\x8a 语音消息 [点击播放]")
              + "</a></span>";
    } else if (msgType == "video" && !fileUrl.isEmpty()) {
        const QString fullUrl = resolveFileUrl(fileUrl);
        const QString encodedUrl = QString::fromUtf8(QUrl::toPercentEncoding(fullUrl));
        html += "<span class='" + bubbleClass + "' style='cursor:pointer;'>"
              + "<a href='msgop://play-voice/" + encodedUrl + "' style='text-decoration:none;color:inherit;'>"
              + QString::fromUtf8("\xf0\x9f\x8e\xac 视频消息 [点击播放]")
              + "</a></span>";
    } else {
        const QString displayContent = readableMessageContent(msgType, content);
        html += "<span class='" + bubbleClass + "'>" + autoLinkUrls(displayContent).replace("\n", "<br>") + "</span>";
    }

    html += "<div class='" + timeClass + "'>" + timeStr + "</div>";
    // Copy link
    const QString copyId = QUuid::createUuid().toString().remove('{').remove('}');
    messageContentById.insert(copyId, content);
    html += "<div class='msg-ops'><a href='msgop://copy/" + copyId + "'>复制</a></div>";
    html += "</div></div>";

    chatBrowser->append(html);
    QTimer::singleShot(50, this, [this]() {
        QTextCursor cursor = chatBrowser->textCursor();
        cursor.movePosition(QTextCursor::End);
        chatBrowser->setTextCursor(cursor);
    });
}

QString MainWindow::avatarColor(const QString &userId) const
{
    // Deterministic color from userId
    uint hash = 0;
    for (int i = 0; i < userId.size(); ++i) {
        hash = hash * 31 + userId.at(i).unicode();
    }
    static const char *colors[] = {
        "#2563eb", "#dc2626", "#16a34a", "#d97706",
        "#7c3aed", "#0891b2", "#be185d", "#4f46e5",
        "#059669", "#ea580c"
    };
    return QString(colors[hash % 10]);
}

QString MainWindow::avatarLetter(const QString &name) const
{
    if (name.isEmpty()) return "?";
    return name.left(1).toUpper();
}

bool MainWindow::isGroupConversation(const QString &conversationId) const
{
    for (int i = 0; i < conversations.size(); ++i) {
        if (conversations.at(i).id == conversationId) {
            return conversations.at(i).type == "group";
        }
    }
    return conversationId.startsWith("g_") || conversationId.contains("group");
}

ConversationItem *MainWindow::currentConversation()
{
    for (int i = 0; i < conversations.size(); ++i) {
        if (conversations[i].id == currentConversationId) {
            return &conversations[i];
        }
    }
    return nullptr;
}

QString MainWindow::resolveFileUrl(const QString &fileUrl) const
{
    if (fileUrl.isEmpty()) return QString();
    // Already absolute
    if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
        return fileUrl;
    }
    // Relative path — prepend base URL
    if (fileUrl.startsWith("/")) {
        return baseUrl() + fileUrl;
    }
    return baseUrl() + "/" + fileUrl;
}

// ---------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------

void MainWindow::applyTheme()
{
    static const QString light = QStringLiteral(
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
        "QListWidget::item:selected{background:#dbeafe;color:#1e4fb3;border-radius:6px;}"
        "QTextBrowser#chatBrowser{border:1px solid #d6dde8;border-radius:8px;background:#f9fafb;padding:12px;font-size:15px;}"
        "QTabWidget::pane{border:1px solid #d6dde8;border-radius:6px;background:#ffffff;}"
        "QTabBar::tab{font-size:14px;padding:8px 18px;border:1px solid transparent;border-bottom:none;border-top-left-radius:6px;border-top-right-radius:6px;}"
        "QTabBar::tab:selected{background:#ffffff;border-color:#d6dde8;color:#1e4fb3;font-weight:600;}"
        "QTabBar::tab:!selected{background:#eef3f8;color:#52627a;}"
        "QSplitter::handle{background:#dde6f1;width:6px;}");

    static const QString dark = QStringLiteral(
        "QMainWindow{background:#1a1a2e;} QWidget{font-family:'Microsoft YaHei','Segoe UI';font-size:15px;color:#e0e0e0;background:#1a1a2e;}"
        "#leftPanel{background:#16213e;border-right:1px solid #2a2a4a;}"
        "#brand{font-size:28px;font-weight:700;color:#e0e0e0;padding:6px 0 0 0;}"
        "#sectionTitle{font-size:22px;font-weight:700;color:#e0e0e0;}"
        "#sideTitle{font-size:14px;font-weight:700;color:#94a3b8;padding:6px 0 0 0;}"
        "#statusPill{font-size:14px;color:#94a3b8;padding:4px 0 8px 0;}"
        "#notice{font-size:15px;padding:12px 14px;border:1px solid #334155;border-radius:6px;background:#1e293b;color:#93c5fd;}"
        "#callPanel{border:1px solid #334155;border-radius:8px;background:#1e293b;}"
        "#callScreen{background:#0f172a;border-radius:8px;}"
        "#callScreenPeer{font-size:18px;font-weight:600;color:#cbd5e1;}"
        "#callScreenTitle{font-size:32px;font-weight:700;color:#ffffff;}"
        "#callScreenState{font-size:16px;color:#93c5fd;}"
        "#callScreenHint{font-size:14px;color:#94a3b8;}"
        "#callAvatar{font-size:56px;font-weight:800;color:#ffffff;background:#2563eb;border-radius:8px;}"
        "QPushButton#acceptCallButton{font-size:18px;font-weight:700;min-width:112px;min-height:46px;border-radius:23px;background:#16a34a;border:1px solid #16a34a;color:#ffffff;}"
        "QPushButton#callDangerButton{font-size:18px;font-weight:700;min-width:112px;min-height:46px;border-radius:23px;background:#dc2626;border:1px solid #dc2626;color:#ffffff;}"
        "QLineEdit{font-size:15px;padding:10px 11px;border:1px solid #334155;border-radius:6px;background:#1e293b;color:#e0e0e0;selection-background-color:#2563eb;}"
        "QLineEdit:focus{border-color:#6096e8;background:#1e293b;}"
        "QPushButton{font-size:15px;font-weight:600;min-height:34px;padding:8px 14px;border:1px solid #334155;border-radius:6px;background:#1e293b;color:#e0e0e0;}"
        "QPushButton:hover{background:#334155;border-color:#6096e8;} QPushButton:pressed{background:#475569;}"
        "QPushButton:disabled{color:#64748b;background:#1a1a2e;border-color:#2a2a4a;}"
        "QPushButton#primaryButton{background:#2563eb;border-color:#2563eb;color:#ffffff;}"
        "QPushButton#primaryButton:hover{background:#1d4ed8;border-color:#1d4ed8;}"
        "QPushButton#dangerButton{color:#fca5a5;border-color:#7f1d1d;background:#450a0a;}"
        "QListWidget,QTextEdit{border:1px solid #334155;border-radius:8px;background:#1e293b;color:#e0e0e0;padding:8px;}"
        "QListWidget::item{font-size:15px;padding:13px 12px;border-bottom:1px solid #2a2a4a;color:#e0e0e0;}"
        "QListWidget::item:selected{background:#1e3a5f;color:#93c5fd;border-radius:6px;}"
        "QTextBrowser#chatBrowser{border:1px solid #334155;border-radius:8px;background:#0f172a;padding:12px;font-size:15px;color:#e0e0e0;}"
        "QTabWidget::pane{border:1px solid #334155;border-radius:6px;background:#1e293b;}"
        "QTabBar::tab{font-size:14px;padding:8px 18px;border:1px solid transparent;border-bottom:none;border-top-left-radius:6px;border-top-right-radius:6px;color:#94a3b8;}"
        "QTabBar::tab:selected{background:#1e293b;border-color:#334155;color:#93c5fd;font-weight:600;}"
        "QTabBar::tab:!selected{background:#16213e;color:#64748b;}"
        "QSplitter::handle{background:#334155;width:6px;}");

    setStyleSheet(darkMode ? dark : light);
}

// ---------------------------------------------------------------------------
// Settings dialog
// ---------------------------------------------------------------------------

void MainWindow::showSettingsDialog()
{
    QDialog dlg(this);
    dlg.setWindowTitle("设置");
    dlg.setMinimumWidth(460);

    auto *scrollArea = new QScrollArea;
    scrollArea->setWidgetResizable(true);
    scrollArea->setFrameShape(QFrame::NoFrame);
    auto *scrollContent = new QWidget;
    auto *mainLayout = new QVBoxLayout(scrollContent);
    mainLayout->setSpacing(16);
    mainLayout->setContentsMargins(20, 20, 20, 20);

    // --- Server connection group ---
    auto *serverGroup = new QGroupBox("服务器连接");
    auto *serverLayout = new QFormLayout;
    serverLayout->setLabelAlignment(Qt::AlignLeft);
    auto *dlgHostEdit = new QLineEdit(hostEdit->text());
    auto *dlgHttpPortEdit = new QLineEdit(httpPortEdit->text());
    auto *dlgTcpPortEdit = new QLineEdit(tcpPortEdit->text());
    serverLayout->addRow("服务器地址", dlgHostEdit);
    serverLayout->addRow("HTTP 端口", dlgHttpPortEdit);
    serverLayout->addRow("TCP 端口", dlgTcpPortEdit);
    serverGroup->setLayout(serverLayout);
    mainLayout->addWidget(serverGroup);

    // --- Chat display group ---
    auto *displayGroup = new QGroupBox("聊天显示");
    auto *displayLayout = new QFormLayout;
    displayLayout->setLabelAlignment(Qt::AlignLeft);

    auto *fontSizeCombo = new QComboBox;
    fontSizeCombo->addItem("小", "small");
    fontSizeCombo->addItem("中", "medium");
    fontSizeCombo->addItem("大", "large");
    const QString savedFontSize = settings.value("chat/fontSize", "medium").toString();
    if (savedFontSize == "small") fontSizeCombo->setCurrentIndex(0);
    else if (savedFontSize == "large") fontSizeCombo->setCurrentIndex(2);
    else fontSizeCombo->setCurrentIndex(1);
    displayLayout->addRow("字体大小", fontSizeCombo);

    auto *showTimestampCheck = new QCheckBox("显示消息时间戳");
    showTimestampCheck->setChecked(settings.value("chat/showTimestamps", true).toBool());
    displayLayout->addRow(showTimestampCheck);

    auto *darkModeCheck = new QCheckBox("深色模式");
    darkModeCheck->setChecked(darkMode);
    displayLayout->addRow(darkModeCheck);

    // Chat background color
    auto *bgColorCombo = new QComboBox;
    bgColorCombo->addItem("默认（白色）", "");
    bgColorCombo->addItem("浅蓝", "#e8f4fd");
    bgColorCombo->addItem("浅绿", "#e8f5e9");
    bgColorCombo->addItem("浅粉", "#fce4ec");
    bgColorCombo->addItem("浅黄", "#fff8e1");
    bgColorCombo->addItem("暖米", "#faf0e6");
    bgColorCombo->addItem("深灰", "#2d2d2d");
    const QString savedBg = settings.value("chat/backgroundColor", "").toString();
    int bgIdx = bgColorCombo->findData(savedBg);
    if (bgIdx >= 0) bgColorCombo->setCurrentIndex(bgIdx);
    displayLayout->addRow("聊天背景", bgColorCombo);

    displayGroup->setLayout(displayLayout);
    mainLayout->addWidget(displayGroup);

    // --- Cache management group ---
    auto *cacheGroup = new QGroupBox("缓存管理");
    auto *cacheLayout = new QVBoxLayout;
    auto *cacheInfoLabel = new QLabel;
    const QString dbPath = QDir(QCoreApplication::applicationDirPath()).filePath("enterprise-im-desktop.sqlite");
    qint64 dbSize = 0;
    QFileInfo dbFi(dbPath);
    if (dbFi.exists()) dbSize = dbFi.size();
    QString dbSizeStr;
    if (dbSize >= 1073741824) dbSizeStr = QString::number(dbSize / 1073741824.0, 'f', 2) + " GB";
    else if (dbSize >= 1048576) dbSizeStr = QString::number(dbSize / 1048576.0, 'f', 2) + " MB";
    else if (dbSize >= 1024) dbSizeStr = QString::number(dbSize / 1024.0, 'f', 2) + " KB";
    else dbSizeStr = QString::number(dbSize) + " B";
    cacheInfoLabel->setText("本地数据库大小：" + dbSizeStr);
    cacheLayout->addWidget(cacheInfoLabel);
    auto *clearCacheBtn = new QPushButton("清除本地缓存");
    clearCacheBtn->setObjectName("dangerButton");
    cacheLayout->addWidget(clearCacheBtn);
    cacheGroup->setLayout(cacheLayout);
    mainLayout->addWidget(cacheGroup);

    connect(clearCacheBtn, &QPushButton::clicked, &dlg, [this, &dlg]() {
        if (QMessageBox::question(&dlg, "清除缓存", "确定清除本地缓存数据吗？所有本地存储的消息和通话记录将被删除。") == QMessageBox::Yes) {
            QSqlDatabase db = QSqlDatabase::database();
            if (db.isOpen()) {
                QSqlQuery q(db);
                q.exec("DELETE FROM messages");
                q.exec("DELETE FROM calls");
                q.exec("VACUUM");
            }
            setActionStatus("本地缓存已清除");
        }
    });

    auto *notifyGroup = new QGroupBox("通知设置");
    auto *notifyLayout = new QVBoxLayout;
    auto *newMessageCheck = new QCheckBox("新消息提醒");
    auto *mentionCheck = new QCheckBox("@我提醒");
    auto *recallCheck = new QCheckBox("撤回提醒");
    auto *screenshotCheck = new QCheckBox("截屏提醒");
    newMessageCheck->setChecked(settings.value("notify/newMessage", true).toBool());
    mentionCheck->setChecked(settings.value("notify/mentionAlert", true).toBool());
    recallCheck->setChecked(settings.value("notify/recallNotice", true).toBool());
    screenshotCheck->setChecked(settings.value("notify/screenshotNotice", false).toBool());
    notifyLayout->addWidget(newMessageCheck);
    notifyLayout->addWidget(mentionCheck);
    notifyLayout->addWidget(recallCheck);
    notifyLayout->addWidget(screenshotCheck);
    notifyGroup->setLayout(notifyLayout);
    mainLayout->addWidget(notifyGroup);

    // --- About group ---
    auto *aboutGroup = new QGroupBox("关于");
    auto *aboutLayout = new QVBoxLayout;
    auto *aboutLabel = new QLabel(
        "<b>企业 IM 桌面端</b><br>"
        "版本: 1.0.0<br>"
        "基于 Qt 5.9.3 构建<br>"
        "支持文字、图片、文件消息及语音/视频通话");
    aboutLabel->setWordWrap(true);
    aboutLabel->setTextFormat(Qt::RichText);
    aboutLayout->addWidget(aboutLabel);
    auto *termsBtn = new QPushButton("查看用户协议");
    termsBtn->setFlat(true);
    termsBtn->setCursor(Qt::PointingHandCursor);
    termsBtn->setStyleSheet("QPushButton { color:#2563eb; text-decoration:underline; border:none; font-size:14px; }");
    connect(termsBtn, &QPushButton::clicked, &dlg, []() {
        QDesktopServices::openUrl(QUrl("https://www.example.com/terms"));
    });
    auto *privacyBtn = new QPushButton("查看隐私政策");
    privacyBtn->setFlat(true);
    privacyBtn->setCursor(Qt::PointingHandCursor);
    privacyBtn->setStyleSheet("QPushButton { color:#2563eb; text-decoration:underline; border:none; font-size:14px; }");
    connect(privacyBtn, &QPushButton::clicked, &dlg, []() {
        QDesktopServices::openUrl(QUrl("https://www.example.com/privacy"));
    });
    aboutLayout->addWidget(termsBtn);
    aboutLayout->addWidget(privacyBtn);
    aboutGroup->setLayout(aboutLayout);
    mainLayout->addWidget(aboutGroup);

    // --- Buttons ---
    auto *buttonBox = new QDialogButtonBox(QDialogButtonBox::Ok | QDialogButtonBox::Cancel);
    buttonBox->button(QDialogButtonBox::Ok)->setText("确定");
    buttonBox->button(QDialogButtonBox::Cancel)->setText("取消");
    mainLayout->addWidget(buttonBox);

    scrollArea->setWidget(scrollContent);
    auto *dlgLayout = new QVBoxLayout(&dlg);
    dlgLayout->setContentsMargins(0, 0, 0, 0);
    dlgLayout->addWidget(scrollArea);
    dlgLayout->addWidget(buttonBox);

    connect(buttonBox, &QDialogButtonBox::accepted, &dlg, &QDialog::accept);
    connect(buttonBox, &QDialogButtonBox::rejected, &dlg, &QDialog::reject);

    if (dlg.exec() != QDialog::Accepted) return;

    // Apply server settings
    hostEdit->setText(dlgHostEdit->text().trimmed());
    httpPortEdit->setText(dlgHttpPortEdit->text().trimmed());
    tcpPortEdit->setText(dlgTcpPortEdit->text().trimmed());

    // Apply display settings
    const QString fontSize = fontSizeCombo->currentData().toString();
    settings.setValue("chat/fontSize", fontSize);
    settings.setValue("chat/showTimestamps", showTimestampCheck->isChecked());
    settings.setValue("display/darkMode", darkModeCheck->isChecked());
    if (darkMode != darkModeCheck->isChecked()) {
        darkMode = darkModeCheck->isChecked();
        applyTheme();
    }
    // Apply chat background
    const QString bgColor = bgColorCombo->currentData().toString();
    settings.setValue("chat/backgroundColor", bgColor);
    if (!bgColor.isEmpty())
        chatBrowser->setStyleSheet(QString("QTextBrowser#chatBrowser{background:%1;}").arg(bgColor));
    else
        chatBrowser->setStyleSheet(QString());

    settings.setValue("notify/newMessage", newMessageCheck->isChecked());
    settings.setValue("notify/mentionAlert", mentionCheck->isChecked());
    settings.setValue("notify/recallNotice", recallCheck->isChecked());
    settings.setValue("notify/screenshotNotice", screenshotCheck->isChecked());

    saveSettings();

    QJsonObject notifyBody;
    notifyBody.insert("newMessage", newMessageCheck->isChecked());
    notifyBody.insert("mentionAlert", mentionCheck->isChecked());
    notifyBody.insert("recallNotice", recallCheck->isChecked());
    notifyBody.insert("screenshotNotice", screenshotCheck->isChecked());
    apiPatch("/api/notification-settings", notifyBody);

    // Reload current conversation to apply new font size
    if (!currentConversationId.isEmpty()) {
        loadMessages(currentConversationId);
    }
    setActionStatus("设置已保存");
}

void MainWindow::showConversationSettingsDialog()
{
    ConversationItem *ci = currentConversation();
    if (!ci) {
        QMessageBox::information(this, "会话设置", "请先选择一个会话。");
        return;
    }

    QDialog dlg(this);
    dlg.setWindowTitle(ci->type == "group" ? "群聊设置" : "单聊设置");
    dlg.setMinimumWidth(420);
    auto *mainLayout = new QVBoxLayout(&dlg);
    mainLayout->setSpacing(14);

    auto *summary = new QLabel(QString("<b>%1</b><br>ID: %2").arg(ci->name.toHtmlEscaped(), ci->id.toHtmlEscaped()));
    summary->setTextFormat(Qt::RichText);
    summary->setWordWrap(true);
    mainLayout->addWidget(summary);

    auto *settingsGroup = new QGroupBox("服务端会话设置");
    auto *settingsLayout = new QVBoxLayout;
    auto *mutedCheck = new QCheckBox("消息免打扰");
    auto *pinnedCheck = new QCheckBox("置顶会话");
    auto *screenshotCheck = new QCheckBox("截屏通知");
    auto *recallCheck = new QCheckBox("撤回通知");
    auto *burnCheck = new QCheckBox("阅后即焚");
    auto *strongCheck = new QCheckBox("@/强提醒");
    auto *nickCheck = new QCheckBox("显示群成员昵称");
    auto *saveGroupCheck = new QCheckBox("保存群到通讯录");
    mutedCheck->setChecked(ci->muted);
    pinnedCheck->setChecked(ci->pinned);
    screenshotCheck->setChecked(ci->screenshotNotice);
    recallCheck->setChecked(ci->recallNotice);
    burnCheck->setChecked(ci->readAfterBurn);
    strongCheck->setChecked(ci->strongReminder);
    nickCheck->setChecked(ci->displayMemberNicknames);
    saveGroupCheck->setChecked(ci->savedToContacts);
    settingsLayout->addWidget(mutedCheck);
    settingsLayout->addWidget(pinnedCheck);
    settingsLayout->addWidget(screenshotCheck);
    settingsLayout->addWidget(recallCheck);
    settingsLayout->addWidget(burnCheck);
    settingsLayout->addWidget(strongCheck);
    if (ci->type == "group") {
        settingsLayout->addWidget(nickCheck);
        settingsLayout->addWidget(saveGroupCheck);

        // Group management actions
        auto *groupMgmtGroup = new QGroupBox("群管理操作");
        auto *groupMgmtLayout = new QVBoxLayout;
        auto *editNameBtn = new QPushButton("编辑群名");
        auto *editNoticeBtn = new QPushButton("编辑公告");
        auto *transferOwnerBtn = new QPushButton("转让群主");
        auto *clearHistoryBtn = new QPushButton("清空聊天记录");
        auto *leaveGroupBtn = new QPushButton("退出群聊");
        leaveGroupBtn->setObjectName("dangerButton");
        clearHistoryBtn->setObjectName("dangerButton");
        groupMgmtLayout->addWidget(editNameBtn);
        groupMgmtLayout->addWidget(editNoticeBtn);
        groupMgmtLayout->addWidget(transferOwnerBtn);
        groupMgmtLayout->addWidget(clearHistoryBtn);
        groupMgmtLayout->addWidget(leaveGroupBtn);
        groupMgmtGroup->setLayout(groupMgmtLayout);
        mainLayout->addWidget(groupMgmtGroup);

        // Member avatar grid (4x5)
        auto *memberGridGroup = new QGroupBox("群成员");
        auto *memberGridLayout = new QGridLayout;
        memberGridLayout->setSpacing(4);
        const QString token = tokenEdit->text().trimmed();
        QNetworkRequest memberReq(QUrl(baseUrl() + "/api/groups/" + QUrl::toPercentEncoding(ci->targetId) + "/members?limit=20"));
        memberReq.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
        if (!token.isEmpty()) memberReq.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
        QNetworkReply *memberReply = http.get(memberReq);
        connect(memberReply, &QNetworkReply::finished, &dlg, [this, memberReply, memberGridLayout, memberGridGroup]() {
            memberReply->deleteLater();
            const QByteArray body = memberReply->readAll();
            const QJsonDocument doc = QJsonDocument::fromJson(body);
            if (!doc.isObject()) return;
            QJsonArray members = doc.object().value("data").toObject().value("members").toArray();
            if (members.isEmpty()) members = doc.object().value("data").toArray();
            for (int mi = 0; mi < qMin(members.size(), 20); ++mi) {
                QJsonObject m = members.at(mi).toObject();
                QString name = m.value("displayName").toString(m.value("nickname").toString(m.value("name").toString()));
                if (name.isEmpty()) name = m.value("userId").toString(m.value("id").toString());
                QChar letter = name.isEmpty() ? '?' : name.at(0).toUpper();
                // Use hash-based color for avatar
                uint hash = qHash(name);
                QStringList colors = {"#2563eb","#16a34a","#dc2626","#ca8a04","#7c3aed","#db2777","#0891b2","#d97706"};
                QString bg = colors[hash % colors.size()];
                auto *avatar = new QLabel(letter);
                avatar->setAlignment(Qt::AlignCenter);
                avatar->setFixedSize(40, 40);
                avatar->setStyleSheet(QString("font-size:16px;font-weight:700;color:#fff;background:%1;border-radius:20px;").arg(bg));
                auto *nameLabel = new QLabel(name.left(4));
                nameLabel->setAlignment(Qt::AlignCenter);
                nameLabel->setStyleSheet("font-size:10px;color:#6b7280;border:none;background:transparent;");
                auto *cell = new QVBoxLayout;
                cell->addWidget(avatar, 0, Qt::AlignCenter);
                cell->addWidget(nameLabel);
                memberGridLayout->addLayout(cell, mi / 5, mi % 5);
            }
        });
        memberGridGroup->setLayout(memberGridLayout);
        mainLayout->addWidget(memberGridGroup);

        // Group nickname setting
        auto *nicknameGroup = new QGroupBox("我在本群的昵称");
        auto *nicknameLayout = new QHBoxLayout;
        auto *nicknameEdit = new QLineEdit;
        nicknameEdit->setPlaceholderText("设置群内昵称...");
        // Fetch current nickname
        QNetworkRequest nickReq(QUrl(baseUrl() + "/api/groups/" + QUrl::toPercentEncoding(ci->targetId) + "/my-nickname"));
        nickReq.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
        if (!token.isEmpty()) nickReq.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
        QNetworkReply *nickReply = http.get(nickReq);
        connect(nickReply, &QNetworkReply::finished, &dlg, [nickReply, nicknameEdit]() {
            nickReply->deleteLater();
            const QByteArray body = nickReply->readAll();
            const QJsonDocument doc = QJsonDocument::fromJson(body);
            if (doc.isObject() && doc.object().value("success").toBool()) {
                QString nick = doc.object().value("data").toObject().value("nickname").toString();
                if (!nick.isEmpty()) nicknameEdit->setText(nick);
            }
        });
        auto *nicknameSaveBtn = new QPushButton("保存");
        nicknameSaveBtn->setObjectName("primaryButton");
        connect(nicknameSaveBtn, &QPushButton::clicked, &dlg, [this, ci, nicknameEdit]() {
            QJsonObject b;
            b.insert("nickname", nicknameEdit->text().trimmed());
            apiPatch("/api/groups/" + QUrl::toPercentEncoding(ci->targetId) + "/my-nickname", b);
            setActionStatus("群昵称已保存");
        });
        nicknameLayout->addWidget(nicknameEdit, 1);
        nicknameLayout->addWidget(nicknameSaveBtn);
        nicknameGroup->setLayout(nicknameLayout);
        mainLayout->addWidget(nicknameGroup);

        // Group ID / QR Code display
        auto *qrGroup = new QGroupBox("群号 / 二维码");
        auto *qrLayout = new QVBoxLayout;
        auto *groupIdLabel = new QLabel("群ID: " + ci->targetId.toHtmlEscaped());
        groupIdLabel->setTextInteractionFlags(Qt::TextSelectableByMouse);
        groupIdLabel->setStyleSheet("font-size:14px;padding:4px;");
        auto *copyIdBtn = new QPushButton("复制群号");
        copyIdBtn->setFlat(true);
        connect(copyIdBtn, &QPushButton::clicked, &dlg, [this, ci]() {
            QApplication::clipboard()->setText(ci->targetId);
            setActionStatus("群号已复制到剪贴板");
        });
        // Placeholder QR code — a bordered square with group ID
        auto *qrPlaceholder = new QLabel;
        qrPlaceholder->setFixedSize(160, 160);
        qrPlaceholder->setAlignment(Qt::AlignCenter);
        qrPlaceholder->setStyleSheet("background:#fff;border:2px solid #d6dde8;font-size:11px;color:#9aa7b8;");
        qrPlaceholder->setText("二维码\n(连接QR生成服务)");
        qrLayout->addWidget(groupIdLabel);
        qrLayout->addWidget(copyIdBtn);
        qrLayout->addWidget(qrPlaceholder, 0, Qt::AlignCenter);
        qrGroup->setLayout(qrLayout);
        mainLayout->addWidget(qrGroup);

        // Connect group management actions (capture pointers by value for the dialog closure)
        connect(editNameBtn, &QPushButton::clicked, &dlg, [this, ci]() {
            bool ok = false;
            const QString name = QInputDialog::getText(this, "编辑群名", "新群名：", QLineEdit::Normal, ci->name, &ok);
            if (ok && !name.trimmed().isEmpty()) {
                QJsonObject b;
                b.insert("name", name.trimmed());
                apiPatch("/api/groups/" + QUrl::toPercentEncoding(ci->targetId) + "/name", b);
                setActionStatus("群名已更新");
                loadConversations();
            }
        });
        connect(editNoticeBtn, &QPushButton::clicked, &dlg, [this, ci]() {
            bool ok = false;
            const QString notice = QInputDialog::getMultiLineText(this, "编辑公告", "群公告：", QString(), &ok);
            if (ok) {
                QJsonObject b;
                b.insert("notice", notice);
                apiPatch("/api/groups/" + QUrl::toPercentEncoding(ci->targetId) + "/notice", b);
                setActionStatus("群公告已更新");
            }
        });
        connect(transferOwnerBtn, &QPushButton::clicked, &dlg, [this, ci]() {
            bool ok = false;
            const QString newOwnerId = QInputDialog::getText(this, "转让群主", "新群主用户ID：", QLineEdit::Normal, QString(), &ok);
            if (ok && !newOwnerId.trimmed().isEmpty()) {
                QJsonObject b;
                b.insert("newOwnerId", newOwnerId.trimmed());
                apiPatch("/api/groups/" + QUrl::toPercentEncoding(ci->targetId) + "/owner", b);
                setActionStatus("群主转让请求已提交");
            }
        });
        connect(clearHistoryBtn, &QPushButton::clicked, &dlg, [this, ci]() {
            if (QMessageBox::question(this, "清空聊天记录", "确定清空该会话的所有聊天记录吗？此操作不可撤销。") == QMessageBox::Yes) {
                // Use DELETE method - sendApiRequest doesn't support DELETE, so use inline call
                const QString token = tokenEdit->text().trimmed();
                QNetworkRequest req(QUrl(baseUrl() + "/api/conversations/" + QUrl::toPercentEncoding(ci->id) + "/messages"));
                req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
                if (!token.isEmpty()) req.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
                QNetworkReply *reply = http.deleteResource(req);
                connect(reply, &QNetworkReply::finished, this, [this, reply]() {
                    reply->deleteLater();
                    setActionStatus("聊天记录已清空");
                    if (!currentConversationId.isEmpty()) loadMessages(currentConversationId);
                });
            }
        });
        connect(leaveGroupBtn, &QPushButton::clicked, &dlg, [this, ci]() {
            if (QMessageBox::question(this, "退出群聊", "确定退出该群聊吗？") == QMessageBox::Yes) {
                QNetworkRequest req(QUrl(baseUrl() + "/api/groups/" + QUrl::toPercentEncoding(ci->targetId)));
                req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
                const QString token = tokenEdit->text().trimmed();
                if (!token.isEmpty()) req.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
                QNetworkReply *reply = http.deleteResource(req);
                connect(reply, &QNetworkReply::finished, this, [this, reply]() {
                    reply->deleteLater();
                    setActionStatus("已退出群聊");
                    loadConversations();
                    loadContacts();
                });
            }
        });
    }
    settingsGroup->setLayout(settingsLayout);
    mainLayout->addWidget(settingsGroup);

    auto *buttonBox = new QDialogButtonBox(QDialogButtonBox::Ok | QDialogButtonBox::Cancel);
    buttonBox->button(QDialogButtonBox::Ok)->setText("保存");
    buttonBox->button(QDialogButtonBox::Cancel)->setText("取消");
    mainLayout->addWidget(buttonBox);
    connect(buttonBox, &QDialogButtonBox::accepted, &dlg, &QDialog::accept);
    connect(buttonBox, &QDialogButtonBox::rejected, &dlg, &QDialog::reject);
    if (dlg.exec() != QDialog::Accepted) return;

    ci->muted = mutedCheck->isChecked();
    ci->pinned = pinnedCheck->isChecked();
    ci->screenshotNotice = screenshotCheck->isChecked();
    ci->recallNotice = recallCheck->isChecked();
    ci->readAfterBurn = burnCheck->isChecked();
    ci->strongReminder = strongCheck->isChecked();
    ci->displayMemberNicknames = nickCheck->isChecked();
    ci->savedToContacts = saveGroupCheck->isChecked();

    QJsonObject body;
    body.insert("muted", ci->muted);
    body.insert("pinned", ci->pinned);
    body.insert("screenshotNotice", ci->screenshotNotice);
    body.insert("recallNotice", ci->recallNotice);
    body.insert("readAfterBurn", ci->readAfterBurn);
    body.insert("strongReminder", ci->strongReminder);
    body.insert("displayMemberNicknames", ci->displayMemberNicknames);
    body.insert("savedToContacts", ci->savedToContacts);
    apiPatch("/api/conversations/" + QUrl::toPercentEncoding(ci->id) + "/settings", body);
    loadConversations();
    setActionStatus("会话设置已提交到服务器");
}

// ---------------------------------------------------------------------------
// File send
// ---------------------------------------------------------------------------

static QString richMessageTypeForFileName(const QString &fileName)
{
    const QString lower = fileName.toLower();
    if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp")) {
        return "image";
    }
    if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".m4v") || lower.endsWith(".webm")) {
        return "video";
    }
    if (lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".aac") || lower.endsWith(".ogg") || lower.endsWith(".m4a") || lower.endsWith(".opus")) {
        return "voice";
    }
    return "file";
}

void MainWindow::pickAndSendFile()
{
    const QString filePath = QFileDialog::getOpenFileName(this, "选择文件");
    if (filePath.isEmpty()) return;
    uploadFile(filePath);
}

void MainWindow::pickEditAndSendImage()
{
    const QString filePath = QFileDialog::getOpenFileName(this, "选择图片", QString(), "Images (*.png *.jpg *.jpeg *.bmp *.webp)");
    if (filePath.isEmpty()) return;
    QImage image(filePath);
    if (image.isNull()) {
        setActionStatus("无法打开图片。", true);
        return;
    }

    // Show a single-step editing dialog with all tools
    QDialog dlg(this);
    dlg.setWindowTitle("图片编辑器");
    dlg.setMinimumSize(700, 550);

    auto *mainLayout = new QHBoxLayout(&dlg);
    auto *toolsLayout = new QVBoxLayout;
    toolsLayout->setSpacing(12);
    toolsLayout->setContentsMargins(12, 12, 12, 12);

    auto *toolsLabel = new QLabel("<b>编辑工具</b>");
    toolsLabel->setTextFormat(Qt::RichText);
    toolsLayout->addWidget(toolsLabel);

    // Rotation
    auto *rotateGroup = new QGroupBox("旋转");
    auto *rotateLayout = new QHBoxLayout;
    auto *rotate90Btn = new QPushButton("旋转90°");
    auto *rotateNeg90Btn = new QPushButton("逆时针90°");
    rotateLayout->addWidget(rotate90Btn);
    rotateLayout->addWidget(rotateNeg90Btn);
    rotateGroup->setLayout(rotateLayout);
    toolsLayout->addWidget(rotateGroup);

    // Filters
    auto *filterGroup = new QGroupBox("滤镜");
    auto *filterLayout = new QVBoxLayout;
    auto *filterCombo = new QComboBox;
    filterCombo->addItem("无滤镜", "none");
    filterCombo->addItem("灰度", "grayscale");
    filterCombo->addItem("复古", "sepia");
    filterCombo->addItem("冷色调", "cool");
    filterCombo->addItem("暖色调", "warm");
    filterLayout->addWidget(filterCombo);
    filterGroup->setLayout(filterLayout);
    toolsLayout->addWidget(filterGroup);

    // Crop
    auto *cropGroup = new QGroupBox("裁剪");
    auto *cropLayout = new QVBoxLayout;
    auto *cropCombo = new QComboBox;
    cropCombo->addItem("不裁剪", "none");
    cropCombo->addItem("1:1 正方形", "1:1");
    cropCombo->addItem("4:3", "4:3");
    cropCombo->addItem("16:9", "16:9");
    cropCombo->addItem("自由(中心)", "free");
    cropLayout->addWidget(cropCombo);
    cropGroup->setLayout(cropLayout);
    toolsLayout->addWidget(cropGroup);

    // Doodle
    auto *doodleGroup = new QGroupBox("涂鸦");
    auto *doodleLayout = new QHBoxLayout;
    auto *doodleCheck = new QCheckBox("添加涂鸦线");
    auto *doodleColorCombo = new QComboBox;
    doodleColorCombo->addItem("红色", "#dc2626");
    doodleColorCombo->addItem("蓝色", "#2563eb");
    doodleColorCombo->addItem("绿色", "#16a34a");
    doodleColorCombo->addItem("黄色", "#f59e0b");
    doodleColorCombo->addItem("白色", "#ffffff");
    doodleColorCombo->addItem("黑色", "#000000");
    doodleLayout->addWidget(doodleCheck);
    doodleLayout->addWidget(doodleColorCombo);
    doodleGroup->setLayout(doodleLayout);
    toolsLayout->addWidget(doodleGroup);

    // Text overlay
    auto *textGroup = new QGroupBox("文字叠加");
    auto *textLayout = new QVBoxLayout;
    auto *textEdit = new QLineEdit;
    textEdit->setPlaceholderText("输入文字内容...");
    auto *textColorCombo = new QComboBox;
    textColorCombo->addItem("白色", "#ffffff");
    textColorCombo->addItem("黑色", "#000000");
    textColorCombo->addItem("红色", "#dc2626");
    textColorCombo->addItem("蓝色", "#2563eb");
    textColorCombo->addItem("黄色", "#f59e0b");
    auto *textSizeSpin = new QSpinBox;
    textSizeSpin->setRange(12, 96);
    textSizeSpin->setValue(24);
    textSizeSpin->setPrefix("字号: ");
    textLayout->addWidget(textEdit);
    textLayout->addWidget(textColorCombo);
    textLayout->addWidget(textSizeSpin);
    textGroup->setLayout(textLayout);
    toolsLayout->addWidget(textGroup);

    toolsLayout->addStretch();

    // Preview
    auto *previewLayout = new QVBoxLayout;
    auto *previewLabel = new QLabel;
    previewLabel->setAlignment(Qt::AlignCenter);
    previewLabel->setMinimumSize(400, 380);
    previewLabel->setStyleSheet("QLabel{border:1px solid #d6dde8;border-radius:6px;background:#f4f7fb;}");
    auto *previewTitle = new QLabel("<b>预览</b>");
    previewTitle->setTextFormat(Qt::RichText);
    previewLayout->addWidget(previewTitle);
    previewLayout->addWidget(previewLabel, 1);

    mainLayout->addLayout(toolsLayout);
    mainLayout->addLayout(previewLayout, 1);

    // Button row
    auto *buttonRow = new QHBoxLayout;
    auto *sendBtn = new QPushButton("发送");
    sendBtn->setObjectName("primaryButton");
    auto *cancelBtn = new QPushButton("取消");
    buttonRow->addStretch();
    buttonRow->addWidget(cancelBtn);
    buttonRow->addWidget(sendBtn);
    previewLayout->addLayout(buttonRow);

    // Preview update function
    int currentRotation = 0;
    auto updatePreview = [&]() {
        QImage preview = image;
        // Apply rotation
        if (currentRotation != 0) {
            QTransform t;
            t.rotate(currentRotation);
            preview = preview.transformed(t);
        }
        // Apply filter
        const QString filter = filterCombo->currentData().toString();
        if (filter == "grayscale") {
            preview = preview.convertToFormat(QImage::Format_Grayscale8);
        } else if (filter == "sepia") {
            preview = preview.convertToFormat(QImage::Format_ARGB32);
            for (int y = 0; y < preview.height(); ++y) {
                QRgb *line = reinterpret_cast<QRgb *>(preview.scanLine(y));
                for (int x = 0; x < preview.width(); ++x) {
                    int r = qRed(line[x]), g = qGreen(line[x]), b = qBlue(line[x]);
                    int tr = qMin(255, static_cast<int>(0.393*r + 0.769*g + 0.189*b));
                    int tg = qMin(255, static_cast<int>(0.349*r + 0.686*g + 0.168*b));
                    int tb = qMin(255, static_cast<int>(0.272*r + 0.534*g + 0.131*b));
                    line[x] = qRgba(tr, tg, tb, qAlpha(line[x]));
                }
            }
        } else if (filter == "cool") {
            preview = preview.convertToFormat(QImage::Format_ARGB32);
            for (int y = 0; y < preview.height(); ++y) {
                QRgb *line = reinterpret_cast<QRgb *>(preview.scanLine(y));
                for (int x = 0; x < preview.width(); ++x) {
                    int r = qRed(line[x]), g = qGreen(line[x]), b = qBlue(line[x]);
                    line[x] = qRgba(r, g, qMin(255, b + 40), qAlpha(line[x]));
                }
            }
        } else if (filter == "warm") {
            preview = preview.convertToFormat(QImage::Format_ARGB32);
            for (int y = 0; y < preview.height(); ++y) {
                QRgb *line = reinterpret_cast<QRgb *>(preview.scanLine(y));
                for (int x = 0; x < preview.width(); ++x) {
                    int r = qRed(line[x]), g = qGreen(line[x]), b = qBlue(line[x]);
                    line[x] = qRgba(qMin(255, r + 40), g, b, qAlpha(line[x]));
                }
            }
        }
        // Apply crop
        const QString crop = cropCombo->currentData().toString();
        if (crop != "none") {
            int cw = preview.width(), ch = preview.height();
            if (crop == "1:1") {
                int side = qMin(cw, ch);
                preview = preview.copy((cw - side) / 2, (ch - side) / 2, side, side);
            } else if (crop == "4:3") {
                int h = qMin(ch, cw * 3 / 4);
                int w = h * 4 / 3;
                preview = preview.copy((cw - w) / 2, (ch - h) / 2, w, h);
            } else if (crop == "16:9") {
                int h = qMin(ch, cw * 9 / 16);
                int w = h * 16 / 9;
                preview = preview.copy((cw - w) / 2, (ch - h) / 2, w, h);
            } else if (crop == "free") {
                int side = qMin(cw, ch);
                preview = preview.copy((cw - side) / 2, (ch - side) / 2, side, side);
            }
        }
        // Apply doodle
        if (doodleCheck->isChecked()) {
            preview = preview.convertToFormat(QImage::Format_ARGB32);
            QPainter painter(&preview);
            QColor dc(doodleColorCombo->currentData().toString());
            QPen pen(dc, qMax(3, preview.width() / 100), Qt::SolidLine, Qt::RoundCap);
            painter.setPen(pen);
            painter.drawLine(10, 10, preview.width() - 10, preview.height() - 10);
            painter.drawLine(10, preview.height() - 10, preview.width() - 10, 10);
            painter.end();
        }
        // Apply text
        if (!textEdit->text().isEmpty()) {
            preview = preview.convertToFormat(QImage::Format_ARGB32);
            QPainter painter(&preview);
            QColor tc(textColorCombo->currentData().toString());
            QFont font("Microsoft YaHei", textSizeSpin->value());
            font.setBold(true);
            painter.setFont(font);
            painter.setPen(tc);
            painter.drawText(preview.rect().adjusted(20, 20, -20, -20), Qt::AlignBottom | Qt::AlignHCenter, textEdit->text());
            painter.end();
        }
        // Scale for preview
        previewLabel->setPixmap(QPixmap::fromImage(preview).scaled(previewLabel->size(), Qt::KeepAspectRatio, Qt::SmoothTransformation));
    };

    // Connect all controls
    connect(rotate90Btn, &QPushButton::clicked, &dlg, [&]() { currentRotation = (currentRotation + 90) % 360; updatePreview(); });
    connect(rotateNeg90Btn, &QPushButton::clicked, &dlg, [&]() { currentRotation = (currentRotation - 90 + 360) % 360; updatePreview(); });
    connect(filterCombo, QOverload<int>::of(&QComboBox::currentIndexChanged), &dlg, [&]() { updatePreview(); });
    connect(cropCombo, QOverload<int>::of(&QComboBox::currentIndexChanged), &dlg, [&]() { updatePreview(); });
    connect(doodleCheck, &QCheckBox::toggled, &dlg, [&]() { updatePreview(); });
    connect(doodleColorCombo, QOverload<int>::of(&QComboBox::currentIndexChanged), &dlg, [&]() { updatePreview(); });
    connect(textEdit, &QLineEdit::textChanged, &dlg, [&]() { updatePreview(); });
    connect(textColorCombo, QOverload<int>::of(&QComboBox::currentIndexChanged), &dlg, [&]() { updatePreview(); });
    connect(textSizeSpin, QOverload<int>::of(&QSpinBox::valueChanged), &dlg, [&]() { updatePreview(); });
    connect(cancelBtn, &QPushButton::clicked, &dlg, &QDialog::reject);
    connect(sendBtn, &QPushButton::clicked, &dlg, &QDialog::accept);

    // Initial preview
    QTimer::singleShot(100, &dlg, updatePreview);

    if (dlg.exec() != QDialog::Accepted) return;

    // Build final image with all edits applied
    QImage finalImage = image;
    if (currentRotation != 0) {
        QTransform t;
        t.rotate(currentRotation);
        finalImage = finalImage.transformed(t);
    }
    const QString filter = filterCombo->currentData().toString();
    if (filter == "grayscale") finalImage = finalImage.convertToFormat(QImage::Format_Grayscale8);
    else if (filter == "sepia" || filter == "cool" || filter == "warm") {
        finalImage = finalImage.convertToFormat(QImage::Format_ARGB32);
        for (int y = 0; y < finalImage.height(); ++y) {
            QRgb *line = reinterpret_cast<QRgb *>(finalImage.scanLine(y));
            for (int x = 0; x < finalImage.width(); ++x) {
                int r = qRed(line[x]), g = qGreen(line[x]), b = qBlue(line[x]);
                if (filter == "sepia") {
                    int tr = qMin(255, static_cast<int>(0.393*r + 0.769*g + 0.189*b));
                    int tg = qMin(255, static_cast<int>(0.349*r + 0.686*g + 0.168*b));
                    int tb = qMin(255, static_cast<int>(0.272*r + 0.534*g + 0.131*b));
                    line[x] = qRgba(tr, tg, tb, qAlpha(line[x]));
                } else if (filter == "cool") {
                    line[x] = qRgba(r, g, qMin(255, b + 40), qAlpha(line[x]));
                } else if (filter == "warm") {
                    line[x] = qRgba(qMin(255, r + 40), g, b, qAlpha(line[x]));
                }
            }
        }
    }
    const QString crop = cropCombo->currentData().toString();
    if (crop != "none") {
        int cw = finalImage.width(), ch = finalImage.height();
        int w = cw, h = ch;
        if (crop == "1:1" || crop == "free") { int s = qMin(cw, ch); w = h = s; }
        else if (crop == "4:3") { h = qMin(ch, cw * 3 / 4); w = h * 4 / 3; }
        else if (crop == "16:9") { h = qMin(ch, cw * 9 / 16); w = h * 16 / 9; }
        finalImage = finalImage.copy((cw - w) / 2, (ch - h) / 2, w, h);
    }
    if (doodleCheck->isChecked()) {
        finalImage = finalImage.convertToFormat(QImage::Format_ARGB32);
        QPainter painter(&finalImage);
        QColor dc(doodleColorCombo->currentData().toString());
        QPen pen(dc, qMax(3, finalImage.width() / 100), Qt::SolidLine, Qt::RoundCap);
        painter.setPen(pen);
        painter.drawLine(10, 10, finalImage.width() - 10, finalImage.height() - 10);
        painter.drawLine(10, finalImage.height() - 10, finalImage.width() - 10, 10);
        painter.end();
    }
    if (!textEdit->text().isEmpty()) {
        finalImage = finalImage.convertToFormat(QImage::Format_ARGB32);
        QPainter painter(&finalImage);
        QColor tc(textColorCombo->currentData().toString());
        QFont font("Microsoft YaHei", textSizeSpin->value());
        font.setBold(true);
        painter.setFont(font);
        painter.setPen(tc);
        painter.drawText(finalImage.rect().adjusted(20, 20, -20, -20), Qt::AlignBottom | Qt::AlignHCenter, textEdit->text());
        painter.end();
    }

    const QString outPath = QDir::tempPath() + "/enterprise_im_qt_edit_" + QString::number(QDateTime::currentMSecsSinceEpoch()) + ".jpg";
    if (!finalImage.save(outPath, "JPG", 92)) {
        setActionStatus("图片编辑保存失败。", true);
        return;
    }
    setActionStatus("图片已编辑，正在上传副本...");
    uploadFile(outPath);
}

void MainWindow::toggleVoiceRecording()
{
    if (voiceRecording) {
        // Stop recording
        voiceRecording = false;
        voiceRecordButton->setText("语音");
        voiceRecordButton->setStyleSheet(QString());
        setActionStatus("录音已停止，正在处理...");

#ifdef Q_OS_WIN
        VoiceRecordCtx *ctx = reinterpret_cast<VoiceRecordCtx *>(voiceRecordCtx);
        if (ctx && ctx->hWaveIn) {
            waveInStop(ctx->hWaveIn);
            waveInReset(ctx->hWaveIn);
            waveInClose(ctx->hWaveIn);

            // Finalize WAV file headers with actual sizes
            if (ctx->file && ctx->file->isOpen()) {
                ctx->file->close();
                QFile wavFile(voiceRecordFilePath);
                if (wavFile.open(QIODevice::ReadWrite)) {
                    DWORD fileSize = static_cast<DWORD>(wavFile.size()) - 8;
                    DWORD dataSize = ctx->totalBytes;
                    // RIFF chunk size
                    wavFile.seek(4);
                    wavFile.write(reinterpret_cast<const char *>(&fileSize), 4);
                    // data chunk size
                    wavFile.seek(40);
                    wavFile.write(reinterpret_cast<const char *>(&dataSize), 4);
                    wavFile.close();
                }
            }
            delete ctx->file;
            delete ctx;
            voiceRecordCtx = nullptr;
        }
#endif

        if (!voiceRecordFilePath.isEmpty() && QFile::exists(voiceRecordFilePath)) {
            uploadFile(voiceRecordFilePath);
        } else {
            setActionStatus("录音文件不可用", true);
        }
        voiceRecordFilePath.clear();
    } else {
        // Start recording
        voiceRecording = true;
        voiceRecordButton->setText("停止录音");
        voiceRecordButton->setStyleSheet("background:#dc2626;color:white;border-color:#dc2626;");
        setActionStatus("正在录音...");

        const QString tempDir = QDir::tempPath();
        voiceRecordFilePath = tempDir + "/im-voice-" + QUuid::createUuid().toString().remove('{').remove('}') + ".wav";

#ifdef Q_OS_WIN
        // Open WAV file with placeholder header
        QFile *wavFile = new QFile(voiceRecordFilePath);
        if (!wavFile->open(QIODevice::WriteOnly)) {
            setActionStatus("无法创建录音文件", true);
            voiceRecording = false;
            voiceRecordButton->setText("语音");
            voiceRecordButton->setStyleSheet(QString());
            delete wavFile;
            return;
        }

        // Write WAV header (will be patched on stop)
        auto writeWavHeader = [](QFile *f, DWORD dataLen) {
            QByteArray hdr;
            hdr.append("RIFF");
            DWORD riffSize = 36 + dataLen;
            hdr.append(reinterpret_cast<const char *>(&riffSize), 4);
            hdr.append("WAVE");
            hdr.append("fmt ");
            DWORD fmtSize = 16;
            hdr.append(reinterpret_cast<const char *>(&fmtSize), 4);
            WORD audioFmt = 1; // PCM
            hdr.append(reinterpret_cast<const char *>(&audioFmt), 2);
            WORD channels = 1; // mono
            hdr.append(reinterpret_cast<const char *>(&channels), 2);
            DWORD sampleRate = 16000;
            hdr.append(reinterpret_cast<const char *>(&sampleRate), 4);
            DWORD byteRate = sampleRate * channels * 2;
            hdr.append(reinterpret_cast<const char *>(&byteRate), 4);
            WORD blockAlign = channels * 2;
            hdr.append(reinterpret_cast<const char *>(&blockAlign), 2);
            WORD bitsPerSample = 16;
            hdr.append(reinterpret_cast<const char *>(&bitsPerSample), 2);
            hdr.append("data");
            hdr.append(reinterpret_cast<const char *>(&dataLen), 4);
            f->write(hdr);
        };
        writeWavHeader(wavFile, 0);

        // Create recording context
        VoiceRecordCtx *ctx = new VoiceRecordCtx;
        ctx->hWaveIn = nullptr;
        ctx->file = wavFile;
        ctx->totalBytes = 0;
        ctx->active = true;
        memset(ctx->buffers, 0, sizeof(ctx->buffers));
        memset(ctx->headers, 0, sizeof(ctx->headers));
        voiceRecordCtx = ctx;

        // Open waveIn device
        WAVEFORMATEX wfx;
        wfx.wFormatTag = WAVE_FORMAT_PCM;
        wfx.nChannels = 1;
        wfx.nSamplesPerSec = 16000;
        wfx.nAvgBytesPerSec = 16000 * 1 * 2;
        wfx.nBlockAlign = 2;
        wfx.wBitsPerSample = 16;
        wfx.cbSize = 0;

        MMRESULT mr = waveInOpen(&ctx->hWaveIn, WAVE_MAPPER, &wfx,
                                  reinterpret_cast<DWORD_PTR>(waveInProc),
                                  reinterpret_cast<DWORD_PTR>(ctx),
                                  CALLBACK_FUNCTION);
        if (mr != MMSYSERR_NOERROR) {
            setActionStatus("麦克风打开失败，请检查设备", true);
            voiceRecording = false;
            voiceRecordButton->setText("语音");
            voiceRecordButton->setStyleSheet(QString());
            wavFile->close();
            delete wavFile;
            delete ctx;
            voiceRecordCtx = nullptr;
            return;
        }

        // Prepare and queue all 4 buffers
        for (int i = 0; i < 4; i++) {
            ctx->headers[i].lpData = ctx->buffers[i];
            ctx->headers[i].dwBufferLength = 8192;
            ctx->headers[i].dwFlags = 0;
            ctx->headers[i].dwUser = reinterpret_cast<DWORD_PTR>(ctx->buffers[i]);
            waveInPrepareHeader(ctx->hWaveIn, &ctx->headers[i], sizeof(WAVEHDR));
            waveInAddBuffer(ctx->hWaveIn, &ctx->headers[i], sizeof(WAVEHDR));
        }

        waveInStart(ctx->hWaveIn);
        setActionStatus("正在录音...再次点击停止");
#else
        voiceRecording = false;
        voiceRecordButton->setText("语音");
        voiceRecordButton->setStyleSheet(QString());
        voiceRecordFilePath.clear();
        setActionStatus("当前平台不支持麦克风录音，未生成占位录音", true);
#endif
    }
}

void MainWindow::playVoice(const QString &fileUrl)
{
    if (fileUrl.isEmpty()) return;
    const QString url = QUrl::fromPercentEncoding(fileUrl.toUtf8());
    setActionStatus("正在播放语音...");

#ifdef ENTERPRISE_IM_HAS_MULTIMEDIA
    // Use QMediaPlayer for playback
    QMediaPlayer *player = new QMediaPlayer(this);
    player->setMedia(QUrl(url));
    connect(player, &QMediaPlayer::stateChanged, this, [this, player](QMediaPlayer::State state) {
        if (state == QMediaPlayer::StoppedState) {
            player->deleteLater();
            setActionStatus("语音播放完成");
        }
    });
    connect(player, static_cast<void (QMediaPlayer::*)(QMediaPlayer::Error)>(&QMediaPlayer::error),
            this, [this, player]() {
                setActionStatus("语音播放失败：" + player->errorString(), true);
                player->deleteLater();
            });
    player->play();
#else
    // Fallback: open in external player
    QDesktopServices::openUrl(QUrl(url));
    setActionStatus("Qt Multimedia 不可用，已尝试在外部播放器中打开");
#endif
}

// ---------------------------------------------------------------------------
// Card message, image viewer, @mention
// ---------------------------------------------------------------------------

void MainWindow::sendCardMessage()
{
    if (contacts.isEmpty()) {
        loadContacts();
        setActionStatus("请先加载通讯录", true);
        return;
    }

    QDialog dlg(this);
    dlg.setWindowTitle("选择联系人名片");
    dlg.setMinimumWidth(360);
    auto *layout = new QVBoxLayout(&dlg);
    auto *listWidget = new QListWidget;
    for (int i = 0; i < contacts.size(); ++i) {
        auto *item = new QListWidgetItem(contacts[i].name + " (" + contacts[i].id + ")");
        item->setData(Qt::UserRole, contacts[i].id);
        item->setData(Qt::UserRole + 1, contacts[i].name);
        listWidget->addItem(item);
    }
    layout->addWidget(listWidget);
    auto *buttonBox = new QDialogButtonBox(QDialogButtonBox::Ok | QDialogButtonBox::Cancel);
    buttonBox->button(QDialogButtonBox::Ok)->setText("发送");
    buttonBox->button(QDialogButtonBox::Cancel)->setText("取消");
    layout->addWidget(buttonBox);
    connect(buttonBox, &QDialogButtonBox::accepted, &dlg, &QDialog::accept);
    connect(buttonBox, &QDialogButtonBox::rejected, &dlg, &QDialog::reject);

    if (dlg.exec() != QDialog::Accepted) return;
    QListWidgetItem *sel = listWidget->currentItem();
    if (!sel) return;

    const QString cardId = sel->data(Qt::UserRole).toString();
    const QString cardName = sel->data(Qt::UserRole + 1).toString();
    QJsonObject cardPayload;
    cardPayload.insert("userId", cardId);
    cardPayload.insert("name", cardName);
    QJsonObject payload;
    payload.insert("content", QJsonValue(cardPayload));
    payload.insert("messageType", "card");
    sendFrame("TEXT", peerIdEdit->text(), conversationIdEdit->text(), payload);
    setActionStatus("名片已发送：" + cardName);

    // Render in chat browser
    const QString html = "<div class='msg-row msg-right'><div class='msg-bubble bubble-right'>"
                         + QString::fromUtf8("\xf0\x9f\x91\xa4 名片: <b>") + cardName.toHtmlEscaped()
                         + "</b><br><small>" + cardId.toHtmlEscaped() + "</small></div></div>";
    chatBrowser->append(html);
}

void MainWindow::showImageViewer(const QStringList &imageUrls, int startIndex)
{
    if (imageUrls.isEmpty()) return;

    QDialog *dlg = new QDialog(this);
    dlg->setWindowTitle("图片查看");
    dlg->resize(900, 680);
    dlg->setStyleSheet("QDialog{background:#000000;}");
    dlg->setAttribute(Qt::WA_DeleteOnClose);

    auto *layout = new QVBoxLayout(dlg);
    layout->setContentsMargins(0, 0, 0, 0);

    auto *label = new QLabel;
    label->setAlignment(Qt::AlignCenter);
    label->setMinimumSize(800, 560);
    label->setScaledContents(false);
    label->setStyleSheet("QLabel{background:#000000;color:#ffffff;font-size:16px;}");

    auto *navLayout = new QHBoxLayout;
    navLayout->setContentsMargins(16, 8, 16, 16);
    auto *prevBtn = new QPushButton(QString::fromUtf8("\xe2\x97\x80 上一张"));
    auto *nextBtn = new QPushButton(QString::fromUtf8("下一张 \xe2\x96\xb6"));
    auto *saveBtn = new QPushButton("保存");
    auto *closeBtn = new QPushButton("关闭");
    prevBtn->setStyleSheet("QPushButton{color:#ffffff;background:#333333;border:1px solid #555;padding:8px 14px;border-radius:4px;}QPushButton:hover{background:#555;}");
    nextBtn->setStyleSheet(prevBtn->styleSheet());
    saveBtn->setStyleSheet(prevBtn->styleSheet());
    closeBtn->setStyleSheet(prevBtn->styleSheet());
    auto *counter = new QLabel;
    counter->setStyleSheet("QLabel{color:#999999;}");
    counter->setAlignment(Qt::AlignCenter);
    navLayout->addWidget(prevBtn);
    navLayout->addWidget(counter, 1);
    navLayout->addWidget(saveBtn);
    navLayout->addWidget(closeBtn);
    navLayout->addWidget(nextBtn);

    layout->addWidget(label, 1);
    layout->addLayout(navLayout);

    int idx = qBound(0, startIndex, imageUrls.size() - 1);
    QPixmap currentPixmap;
    double currentScale = 1.0;

    auto updateImage = [&]() {
        const QUrl url(resolveFileUrl(imageUrls.at(idx)));
        QNetworkRequest req(url);
        const QString token = tokenEdit->text().trimmed();
        if (!token.isEmpty()) req.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
        QNetworkReply *reply = http.get(req);
        connect(reply, &QNetworkReply::finished, dlg, [dlg, reply, label, counter, idx, &imageUrls, &currentPixmap, &currentScale]() {
            reply->deleteLater();
            if (reply->error() != QNetworkReply::NoError) {
                label->setText("加载失败: " + reply->errorString());
                return;
            }
            currentPixmap.loadFromData(reply->readAll());
            if (!currentPixmap.isNull()) {
                currentScale = 1.0;
                label->setPixmap(currentPixmap.scaled(label->size(), Qt::KeepAspectRatio, Qt::SmoothTransformation));
            }
            counter->setText(QString("%1 / %2").arg(idx + 1).arg(imageUrls.size()));
        });
        counter->setText(QString("加载中... %1 / %2").arg(idx + 1).arg(imageUrls.size()));
    };

    updateImage();

    connect(prevBtn, &QPushButton::clicked, dlg, [&]() {
        if (idx > 0) { --idx; currentScale = 1.0; updateImage(); }
    });
    connect(nextBtn, &QPushButton::clicked, dlg, [&]() {
        if (idx < imageUrls.size() - 1) { ++idx; currentScale = 1.0; updateImage(); }
    });
    connect(saveBtn, &QPushButton::clicked, dlg, [&]() {
        if (!currentPixmap.isNull()) {
            const QString savePath = QFileDialog::getSaveFileName(dlg, "保存图片", QString(), "Images (*.jpg *.png)");
            if (!savePath.isEmpty()) currentPixmap.save(savePath);
        }
    });
    connect(closeBtn, &QPushButton::clicked, dlg, &QDialog::close);

    // Mouse wheel zoom
    dlg->installEventFilter(dlg);
    label->setMouseTracking(true);
    label->installEventFilter(label);
    // Use wheelEvent override on label for zoom (handled via eventFilter on dialog)
    dlg->setProperty("viewerLabel", QVariant::fromValue(reinterpret_cast<quintptr>(label)));
    dlg->setProperty("viewerPixmap", QVariant::fromValue(reinterpret_cast<quintptr>(&currentPixmap)));
    dlg->setProperty("viewerScale", QVariant::fromValue(reinterpret_cast<quintptr>(&currentScale)));

    dlg->exec();
}

void MainWindow::showAtMentionPicker()
{
    // Only for group conversations
    if (!isGroupConversation(currentConversationId)) {
        QMessageBox::information(this, "提醒", "仅群聊会话支持 @提醒 功能");
        return;
    }
    // Load group members from the group member API
    ConversationItem *ci = currentConversation();
    if (!ci || ci->targetId.isEmpty()) return;

    const QString path = "/api/groups/" + ci->targetId + "/members";
    QNetworkRequest req(QUrl(baseUrl() + path));
    const QString token = tokenEdit->text().trimmed();
    if (!token.isEmpty()) req.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
    QNetworkReply *reply = http.get(req);
    connect(reply, &QNetworkReply::finished, this, [this, reply]() {
        reply->deleteLater();
        const QByteArray body = reply->readAll();
        const QJsonDocument doc = QJsonDocument::fromJson(body);
        if (!doc.isObject() || !doc.object().value("success").toBool()) return;
        const QJsonArray members = doc.object().value("data").toObject().value("members").toArray();

        QDialog dlg(this);
        dlg.setWindowTitle("选择提醒成员");
        dlg.setMinimumWidth(320);
        auto *layout = new QVBoxLayout(&dlg);
        auto *list = new QListWidget;
        for (int i = 0; i < members.size(); ++i) {
            const QJsonObject m = members[i].toObject();
            const QString uid = m.value("userId").toString(m.value("id").toString());
            const QString uname = m.value("name").toString(m.value("nickname").toString(uid));
            auto *item = new QListWidgetItem(uname + " (" + uid + ")");
            item->setData(Qt::UserRole, uid);
            item->setData(Qt::UserRole + 1, uname);
            list->addItem(item);
        }
        layout->addWidget(list);
        auto *box = new QDialogButtonBox(QDialogButtonBox::Ok | QDialogButtonBox::Cancel);
        box->button(QDialogButtonBox::Ok)->setText("插入 @");
        box->button(QDialogButtonBox::Cancel)->setText("取消");
        layout->addWidget(box);
        connect(box, &QDialogButtonBox::accepted, &dlg, &QDialog::accept);
        connect(box, &QDialogButtonBox::rejected, &dlg, &QDialog::reject);
        if (dlg.exec() != QDialog::Accepted) return;
        QListWidgetItem *sel = list->currentItem();
        if (!sel) return;
        const QString name = sel->data(Qt::UserRole + 1).toString();
        messageEdit->setText(messageEdit->text() + "@" + name + " ");
    });
}

void MainWindow::uploadFile(const QString &filePath)
{
    const QFileInfo fileInfo(filePath);
    if (fileInfo.size() >= 1024 * 1024) {
        startChunkUpload(filePath);
        return;
    }

    QFile *file = new QFile(filePath);
    if (!file->open(QIODevice::ReadOnly)) {
        setActionStatus("无法打开文件：" + filePath, true);
        delete file;
        return;
    }

    const QFileInfo fi(filePath);
    QHttpMultiPart *multiPart = new QHttpMultiPart(QHttpMultiPart::FormDataType);
    QHttpPart uploaderPart;
    uploaderPart.setHeader(QNetworkRequest::ContentDispositionHeader, QVariant("form-data; name=\"uploaderId\""));
    uploaderPart.setBody(userIdEdit->text().trimmed().toUtf8());
    multiPart->append(uploaderPart);

    QHttpPart filePart;
    filePart.setHeader(QNetworkRequest::ContentDispositionHeader,
                       QVariant("form-data; name=\"file\"; filename=\"" + fi.fileName() + "\""));
    filePart.setBodyDevice(file);
    file->setParent(multiPart); // auto-delete
    multiPart->append(filePart);

    QNetworkRequest request(QUrl(baseUrl() + "/api/files/upload"));
    const QString token = tokenEdit->text().trimmed();
    if (!token.isEmpty()) {
        request.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
    }

    setActionStatus("正在上传文件：" + fi.fileName());
    QNetworkReply *reply = http.post(request, multiPart);
    multiPart->setParent(reply);
    QProgressDialog *progress = new QProgressDialog("正在上传文件...", "取消", 0, 100, this);
    progress->setWindowModality(Qt::WindowModal);
    progress->setAutoClose(true);
    progress->show();
    connect(progress, &QProgressDialog::canceled, reply, &QNetworkReply::abort);
    connect(reply, &QNetworkReply::uploadProgress, this, [this, progress](qint64 sent, qint64 total) {
        if (total > 0) {
            const int percent = static_cast<int>((sent * 100) / total);
            progress->setValue(percent);
            setActionStatus(QString("文件上传进度 %1%").arg(percent));
        }
    });

    connect(reply, &QNetworkReply::finished, this, [this, reply, progress, fi]() {
        progress->setValue(100);
        progress->deleteLater();
        const QByteArray body = reply->readAll();
        if (reply->error() != QNetworkReply::NoError) {
            setActionStatus("文件上传失败：" + reply->errorString(), true);
            appendLog("文件上传失败：" + reply->errorString());
        } else {
            handleFileUploadResponse(body);
        }
        reply->deleteLater();
    });
}

void MainWindow::handleFileUploadResponse(const QByteArray &body)
{
    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject()) {
        setActionStatus("文件上传返回格式错误", true);
        return;
    }
    const QJsonObject root = doc.object();
    if (!root.value("success").toBool()) {
        setActionStatus("文件上传失败：" + root.value("error").toString(), true);
        return;
    }
    const QJsonObject data = root.value("data").toObject();
    const QString fileId = data.value("id").toString(data.value("fileId").toString());
    const QString fileName = data.value("originalName").toString(data.value("name").toString(data.value("filename").toString()));
    const QString fileUrl = data.value("previewUrl").toString(data.value("downloadUrl").toString(data.value("url").toString(data.value("path").toString())));
    const qint64 fileSize = static_cast<qint64>(data.value("sizeBytes").toDouble(data.value("fileSize").toDouble(0)));
    const QString messageType = richMessageTypeForFileName(fileName);
    if (activeUploadQueueId >= 0) {
        deleteQueuedUpload(activeUploadQueueId);
        activeUploadQueueId = -1;
    }
    uploadQueueResuming = false;

    if (fileId.isEmpty() && fileUrl.isEmpty()) {
        setActionStatus("文件上传成功但返回数据不完整", true);
        return;
    }

    // Send file message via TCP
    QJsonObject payload;
    payload.insert("content", (messageType == "image" ? "[图片] " : (messageType == "video" ? "[视频] " : (messageType == "voice" ? "[语音] " : "[文件] "))) + fileName);
    payload.insert("fileId", fileId);
    payload.insert("fileName", fileName);
    payload.insert("fileUrl", fileUrl);
    payload.insert("fileSize", static_cast<double>(fileSize));
    payload.insert("messageType", messageType);
    sendFrame("TEXT", peerIdEdit->text(), conversationIdEdit->text(), payload);

    setActionStatus("文件已上传并发送：" + fileName);
    appendLog("文件上传成功 fileId=" + fileId + " name=" + fileName);
}

void MainWindow::startChunkUpload(const QString &filePath)
{
    const QFileInfo fi(filePath);
    chunkUploadFilePath = filePath;
    chunkUploadOriginalName = fi.fileName();
    chunkUploadTotalBytes = fi.size();
    chunkUploadTotalChunks = qMax(1, static_cast<int>((chunkUploadTotalBytes + 524287) / 524288));
    chunkUploadNextChunk = 0;
    chunkUploadRetryCount = 0;
    chunkUploadSessionId.clear();

    QJsonObject body;
    body.insert("uploaderId", userIdEdit->text().trimmed());
    body.insert("originalName", chunkUploadOriginalName);
    body.insert("contentType", "application/octet-stream");
    body.insert("totalSize", static_cast<double>(chunkUploadTotalBytes));
    body.insert("totalChunks", chunkUploadTotalChunks);
    setActionStatus(QString("创建分片上传任务：%1 片").arg(chunkUploadTotalChunks));
    apiPost("/api/files/chunk-upload/sessions", body);
}

void MainWindow::handleChunkSessionResponse(const QByteArray &body)
{
    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject() || !doc.object().value("success").toBool()) {
        setActionStatus("分片上传任务创建失败", true);
        return;
    }
    chunkUploadSessionId = doc.object().value("data").toObject().value("id").toString();
    if (chunkUploadSessionId.isEmpty()) {
        setActionStatus("分片上传任务缺少 sessionId", true);
        return;
    }
    uploadNextChunk();
}

void MainWindow::uploadNextChunk()
{
    if (chunkUploadSessionId.isEmpty()) return;
    if (chunkUploadNextChunk >= chunkUploadTotalChunks) {
        completeChunkUpload();
        return;
    }

    QFile file(chunkUploadFilePath);
    if (!file.open(QIODevice::ReadOnly)) {
        setActionStatus("无法读取分片文件。", true);
        return;
    }
    const qint64 chunkSize = 524288;
    file.seek(static_cast<qint64>(chunkUploadNextChunk) * chunkSize);
    const QByteArray bytes = file.read(chunkSize);
    file.close();

    QHttpMultiPart *multiPart = new QHttpMultiPart(QHttpMultiPart::FormDataType);
    QHttpPart filePart;
    filePart.setHeader(QNetworkRequest::ContentDispositionHeader,
                       QVariant("form-data; name=\"file\"; filename=\"" + chunkUploadOriginalName + ".part" + QString::number(chunkUploadNextChunk) + "\""));
    filePart.setBody(bytes);
    multiPart->append(filePart);

    QNetworkRequest request(QUrl(baseUrl() + "/api/files/chunk-upload/sessions/" + chunkUploadSessionId + "/chunks?chunkIndex=" + QString::number(chunkUploadNextChunk)));
    const QString token = tokenEdit->text().trimmed();
    if (!token.isEmpty()) request.setRawHeader("Authorization", ("Bearer " + token).toUtf8());

    QNetworkReply *reply = http.post(request, multiPart);
    multiPart->setParent(reply);
    const int currentChunk = chunkUploadNextChunk;
    connect(reply, &QNetworkReply::finished, this, [this, reply, currentChunk]() {
        const QByteArray body = reply->readAll();
        const QString err = reply->errorString();
        const bool networkOk = reply->error() == QNetworkReply::NoError;
        reply->deleteLater();
        const QJsonDocument doc = QJsonDocument::fromJson(body);
        const bool apiOk = doc.isObject() && doc.object().value("success").toBool();
        if (!networkOk || !apiOk) {
            if (chunkUploadRetryCount < 2) {
                chunkUploadRetryCount++;
                setActionStatus(QString("分片 %1 失败，重试 %2/3").arg(currentChunk + 1).arg(chunkUploadRetryCount + 1), true);
                uploadNextChunk();
                return;
            }
            setActionStatus("分片上传失败：" + err, true);
            if (!uploadQueueResuming) saveQueuedUpload(chunkUploadFilePath, err);
            uploadQueueResuming = false;
            return;
        }
        chunkUploadRetryCount = 0;
        chunkUploadNextChunk++;
        const int percent = static_cast<int>((static_cast<qint64>(chunkUploadNextChunk) * 100) / qMax<qint64>(1, chunkUploadTotalChunks));
        setActionStatus(QString("分片上传进度 %1/%2 (%3%)").arg(chunkUploadNextChunk).arg(chunkUploadTotalChunks).arg(percent));
        uploadNextChunk();
    });
}

void MainWindow::completeChunkUpload()
{
    setActionStatus("分片上传完成，正在合并文件...");
    apiPost("/api/files/chunk-upload/sessions/" + chunkUploadSessionId + "/complete", QJsonObject());
}

// ---------------------------------------------------------------------------
// TCP connection and auth
// ---------------------------------------------------------------------------

void MainWindow::connectToServer()
{
    reconnectTimer->stop();
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

void MainWindow::favoriteLastMessage()
{
    favoriteMessage(lastMessageId);
    return;
    if (lastMessageId.isEmpty()) {
        setActionStatus("没有可收藏的服务端消息，请先发送或接收一条消息。", true);
        return;
    }
    setActionStatus("正在收藏最近消息...");
    apiPost("/api/messages/" + lastMessageId + "/favorite", QJsonObject());
}

void MainWindow::likeLastMessage()
{
    likeMessage(lastMessageId);
    return;
    if (lastMessageId.isEmpty()) {
        setActionStatus("没有可点赞的服务端消息，请先发送或接收一条消息。", true);
        return;
    }
    QJsonObject body;
    body.insert("reaction", "like");
    setActionStatus("正在点赞最近消息...");
    apiPost("/api/messages/" + lastMessageId + "/reactions", body);
}

void MainWindow::recallLastMessage()
{
    recallMessage(lastMessageId);
    return;
    if (lastMessageId.isEmpty()) {
        setActionStatus("没有可撤回的服务端消息，请先发送一条消息。", true);
        return;
    }
    QJsonObject body;
    body.insert("reason", "desktop_recall");
    setActionStatus("正在撤回最近消息...");
    apiPost("/api/messages/" + lastMessageId + "/recall", body);
}

void MainWindow::editLastMessage()
{
    editMessage(lastMessageId);
    return;
    if (lastMessageId.isEmpty()) {
        setActionStatus("没有可编辑的服务端消息，请先发送一条消息。", true);
        return;
    }
    bool ok = false;
    const QString content = QInputDialog::getText(this, "编辑最近消息", "新内容", QLineEdit::Normal, messageEdit->text(), &ok).trimmed();
    if (!ok || content.isEmpty()) {
        return;
    }
    QJsonObject body;
    body.insert("content", content);
    setActionStatus("正在编辑最近消息...");
    apiPatch("/api/messages/" + lastMessageId + "/edit", body);
}

void MainWindow::handleMessageActionLink(const QUrl &url)
{
    const QString action = url.host();
    QString messageId = url.path();
    if (messageId.startsWith("/")) {
        messageId.remove(0, 1);
    }
    messageId = QUrl::fromPercentEncoding(messageId.toUtf8());

    if (action == "favorite") {
        favoriteMessage(messageId);
    } else if (action == "like") {
        likeMessage(messageId);
    } else if (action == "recall") {
        recallMessage(messageId);
    } else if (action == "edit") {
        editMessage(messageId);
    } else if (action == "select") {
        toggleMessageSelection(messageId);
    } else if (action == "forward") {
        forwardMessages(QStringList() << messageId);
    } else if (action == "readstatus") {
        showReadStatus(messageId);
    } else if (action == "copy") {
        const QString content = messageContentById.value(messageId);
        if (!content.isEmpty()) {
            QApplication::clipboard()->setText(content);
            setActionStatus("已复制到剪贴板");
        } else {
            setActionStatus("复制失败：未找到消息内容", true);
        }
    } else if (action == "play-voice") {
        playVoice(messageId);
    } else if (action == "view-image") {
        // messageId is already the full URL from the percent-encoded path
        showImageViewer(QStringList() << messageId, 0);
    }
}

void MainWindow::favoriteMessage(const QString &messageId)
{
    if (messageId.isEmpty()) {
        setActionStatus("没有可收藏的服务端消息。", true);
        return;
    }
    lastMessageId = messageId;
    setActionStatus("正在收藏选中消息...");
    apiPost("/api/messages/" + messageId + "/favorite", QJsonObject());
}

void MainWindow::likeMessage(const QString &messageId)
{
    if (messageId.isEmpty()) {
        setActionStatus("没有可点赞的服务端消息。", true);
        return;
    }
    QJsonObject body;
    body.insert("reaction", "like");
    lastMessageId = messageId;
    setActionStatus("正在点赞选中消息...");
    apiPost("/api/messages/" + messageId + "/reactions", body);
}

void MainWindow::recallMessage(const QString &messageId)
{
    if (messageId.isEmpty()) {
        setActionStatus("没有可撤回的服务端消息。", true);
        return;
    }
    QJsonObject body;
    body.insert("reason", "desktop_recall");
    lastMessageId = messageId;
    setActionStatus("正在撤回选中消息...");
    apiPost("/api/messages/" + messageId + "/recall", body);
}

void MainWindow::editMessage(const QString &messageId)
{
    if (messageId.isEmpty()) {
        setActionStatus("没有可编辑的服务端消息。", true);
        return;
    }
    bool ok = false;
    const QString currentContent = messageContentById.value(messageId, messageEdit->text());
    const QString content = QInputDialog::getText(this, "编辑消息", "新内容", QLineEdit::Normal, currentContent, &ok).trimmed();
    if (!ok || content.isEmpty()) {
        return;
    }
    QJsonObject body;
    body.insert("content", content);
    lastMessageId = messageId;
    setActionStatus("正在编辑选中消息...");
    apiPatch("/api/messages/" + messageId + "/edit", body);
}

void MainWindow::showReadStatus(const QString &messageId)
{
    if (messageId.isEmpty()) return;
    setActionStatus("正在读取已读明细...");
    apiGet("/api/messages/" + messageId + "/read-status");
}

void MainWindow::toggleMessageSelection(const QString &messageId)
{
    if (messageId.isEmpty()) return;
    if (selectedMessageIds.contains(messageId)) {
        selectedMessageIds.remove(messageId);
    } else {
        selectedMessageIds.insert(messageId);
    }
    setActionStatus(QString("已选择 %1 条消息，可点击批量转发。").arg(selectedMessageIds.size()));
}

void MainWindow::forwardSelectedMessages()
{
    if (selectedMessageIds.isEmpty()) {
        setActionStatus("请先在消息下方点“选择”。", true);
        return;
    }
    QStringList ids;
    for (const QString &id : selectedMessageIds) ids << id;
    forwardMessages(ids);
}

void MainWindow::forwardMessages(const QStringList &messageIds)
{
    if (messageIds.isEmpty()) return;
    bool ok = false;
    const QString targetText = QInputDialog::getText(this, "批量转发", "目标会话ID，多个用逗号分隔", QLineEdit::Normal, currentConversationId, &ok).trimmed();
    if (!ok || targetText.isEmpty()) return;
    QStringList targets;
    for (const QString &part : targetText.split(',', QString::SkipEmptyParts)) {
        const QString target = part.trimmed();
        if (!target.isEmpty()) targets << target;
    }
    if (targets.isEmpty()) return;

    const QMessageBox::StandardButton modeButton = QMessageBox::question(
        this, "转发模式", "是否合并转发？\nYes=合并，No=逐条",
        QMessageBox::Yes | QMessageBox::No | QMessageBox::Cancel,
        messageIds.size() > 1 ? QMessageBox::Yes : QMessageBox::No);
    if (modeButton == QMessageBox::Cancel) return;

    QJsonArray messageArray;
    for (const QString &id : messageIds) messageArray.append(id);
    QJsonArray targetArray;
    for (const QString &id : targets) targetArray.append(id);
    QJsonObject body;
    body.insert("messageIds", messageArray);
    body.insert("targetConversationIds", targetArray);
    body.insert("mode", modeButton == QMessageBox::Yes ? "combine" : "single");
    setActionStatus(QString("正在转发 %1 条消息...").arg(messageIds.size()));
    apiPost("/api/messages/forward", body);
    selectedMessageIds.clear();
}

// ---------------------------------------------------------------------------
// Call operations
// ---------------------------------------------------------------------------

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
        sipMediaClient->stop();
        nativeStartedCallId.clear();
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

// ---------------------------------------------------------------------------
// TCP frame handling
// ---------------------------------------------------------------------------

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
        appendChatBubble("系统", "桌面端已登录：" + frame.value("to").toString(userIdEdit->text()),
                         QDateTime::currentDateTime().toString(Qt::ISODate), false);
    } else if (type == "PONG") {
        setActionStatus("心跳成功，当前连接正常。");
    } else if (type == "ACK") {
        const QString messageId = payload.value("messageId").toString();
        if (!messageId.isEmpty()) {
            lastMessageId = messageId;
            // Store the last sent content with the real messageId
            const QString ackContent = payload.value("content").toString(messageEdit->text());
            if (!ackContent.isEmpty()) {
                messageContentById.insert(messageId, ackContent);
            }
            appendLog("最近消息 ID=" + lastMessageId);
        }
        setActionStatus("服务端已收到刚才的操作。");
    } else if (type == "TEXT_DELIVER") {
        const QString messageId = payload.value("messageId").toString();
        const QString deliverContent = payload.value("content").toString();
        if (!messageId.isEmpty()) {
            lastMessageId = messageId;
            if (!deliverContent.isEmpty()) {
                messageContentById.insert(messageId, deliverContent);
            }
            appendLog("最近消息 ID=" + lastMessageId);
        }
        const QString from = frame.value("from").toString("对方");
        const QString pMsgType = payload.value("messageType").toString(payload.value("type").toString());
        const QString pFileUrl = payload.value("fileUrl").toString(payload.value("url").toString());
        const QString pFileName = payload.value("fileName").toString(payload.value("name").toString());
        const qint64 pFileSize = static_cast<qint64>(payload.value("fileSize").toDouble(payload.value("size").toDouble(0)));
        appendChatBubble(from, deliverContent,
                         QDateTime::currentDateTime().toString(Qt::ISODate), false,
                         pMsgType, pFileUrl, pFileName, pFileSize);
        setActionStatus("收到来自 " + from + " 的消息。");
    } else if (type == "TYPING_DELIVER") {
        const QString from = frame.value("from").toString();
        const bool isTyping = payload.value("isTyping").toBool(false);
        if (isTyping && !from.isEmpty()) {
            // Resolve sender name
            QString name = from;
            for (int i = 0; i < contacts.size(); ++i) {
                if (contacts.at(i).id == from) {
                    name = contacts.at(i).name;
                    break;
                }
            }
            typingPeerName = name;
            chatTitleLabel->setText(name + " 正在输入...");
            // Auto-clear after 5 seconds
            QTimer::singleShot(5000, this, [this]() { clearTypingIndicator(); });
        } else {
            clearTypingIndicator();
        }
    } else if ((type == "CALL_INVITE" || type == "CALL_UPDATE") && payload.contains("id")) {
        activeCallId = payload.value("id").toString();
        activeMediaType = payload.value("mediaType").toString();
        activeCallIncoming = payload.value("calleeId").toString() == userIdEdit->text();
        updateCallStatus(activeMediaType + " / " + payload.value("status").toString());
        const QString status = payload.value("status").toString();
        const bool mediaReady = payload.value("mediaStatus").toString() == "media_ready";
        if (status == "answered" && mediaReady && nativeStartedCallId != activeCallId) {
            requestMediaConfig();
        }
        if (status == "rejected" || status == "ended") {
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
        reconnectAttempts = 0;
        sendAuth();
        setActionStatus("TCP 已连接。正在自动认证...");
    } else {
        setActionStatus("TCP 未连接。");
    }
    appendLog(connected ? "TCP 已连接" : "TCP 已断开");
}

void MainWindow::scheduleReconnect()
{
    if (!loggedIn || closing || tokenEdit->text().trimmed().isEmpty()) return;
    if (socket.state() != QAbstractSocket::UnconnectedState) return;
    if (reconnectTimer->isActive()) return;
    reconnectAttempts = qMin(reconnectAttempts + 1, 6);
    const int delayMs = qMin(30000, 1000 * (1 << qMin(reconnectAttempts - 1, 5)));
    setActionStatus(QString("TCP disconnected. Reconnecting in %1s...").arg(delayMs / 1000));
    appendLog(QString("TCP auto reconnect scheduled in %1 ms").arg(delayMs));
    reconnectTimer->start(delayMs);
}

void MainWindow::appendLog(const QString &line)
{
    QFile logFile(QCoreApplication::applicationDirPath() + "/enterprise-im-qt.log");
    if (logFile.open(QIODevice::Append | QIODevice::Text)) {
        QTextStream out(&logFile);
        out << QDateTime::currentDateTime().toString(Qt::ISODate) << " " << line << "\n";
    }

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
    } else if (line.contains("no active codec") || line.contains("Found 0 video codecs")) {
        text = "Qt pjsua 缺少 H264/VP8 视频编码器，桌面端会拒绝视频 SDP。";
        setActionStatus(text, true);
        callScreenHint->setText(text);
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
    const bool isSelf = (sender == userIdEdit->text());
    appendChatBubble(sender, content, QDateTime::currentDateTime().toString(Qt::ISODate), isSelf);
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
    chatBrowser->setVisible(false);
    composerPanel->setVisible(false);
    callPanel->setVisible(false);
}

void MainWindow::hideCallScreen()
{
    callScreen->setVisible(false);
    chatBrowser->setVisible(true);
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

// ---------------------------------------------------------------------------
// TCP frame sending
// ---------------------------------------------------------------------------

void MainWindow::sendFrame(const QString &type, const QString &to, const QString &conversationId, const QJsonObject &payload)
{
    if (socket.state() != QAbstractSocket::ConnectedState) {
        setActionStatus("还没连接服务端，请先连接。", true);
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
        const QString pMsgType = payload.value("messageType").toString();
        const QString pFileUrl = payload.value("fileUrl").toString();
        const QString pFileName = payload.value("fileName").toString();
        const qint64 pFileSize = static_cast<qint64>(payload.value("fileSize").toDouble(0));
        if (pMsgType == "image" || pMsgType == "file") {
            appendChatBubble(userIdEdit->text(), payload.value("content").toString(),
                             QDateTime::currentDateTime().toString(Qt::ISODate), true,
                             pMsgType, pFileUrl, pFileName, pFileSize);
        } else {
            appendMessage(userIdEdit->text(), payload.value("content").toString());
        }
        messageEdit->clear();
        setActionStatus("消息已发送，等待服务端确认。");
    }
    appendLog("发送 " + type + "：" + QString::fromUtf8(json));
}

void MainWindow::sendTextMessage(const QString &conversationId, const QString &to, const QString &content)
{
    QJsonObject payload;
    payload.insert("content", content);
    sendFrame("TEXT", to, conversationId, payload);
}

void MainWindow::sendTyping(bool isTyping)
{
    if (currentConversationId.isEmpty()) return;
    QJsonObject payload;
    payload.insert("isTyping", isTyping);
    sendFrame("TYPING", peerIdEdit->text(), conversationIdEdit->text(), payload);
}

void MainWindow::clearTypingIndicator()
{
    typingPeerName.clear();
    if (currentConversationId.isEmpty()) return;
    ConversationItem *ci = currentConversation();
    chatTitleLabel->setText(ci ? ci->name : "选择一个会话开始聊天");
}

void MainWindow::pollGroupOnlineStatus()
{
    if (currentConversationId.isEmpty()) return;
    ConversationItem *ci = currentConversation();
    if (!ci || ci->type != "group") return;

    const QString token = tokenEdit->text().trimmed();
    QNetworkRequest req(QUrl(baseUrl() + "/api/online-status?groupId=" + QUrl::toPercentEncoding(ci->targetId)));
    req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    if (!token.isEmpty()) req.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
    QNetworkReply *reply = http.get(req);
    connect(reply, &QNetworkReply::finished, this, [this, reply, ci]() {
        reply->deleteLater();
        if (reply->error() != QNetworkReply::NoError) return;
        const QByteArray body = reply->readAll();
        const QJsonDocument doc = QJsonDocument::fromJson(body);
        if (!doc.isObject()) return;
        const QJsonObject root = doc.object();
        int online = root.value("data").toObject().value("online").toInt(-1);
        if (online >= 0) {
            chatTitleLabel->setText(ci->name + QString("  [%1人在线]").arg(online));
        }
    });
}

// ---------------------------------------------------------------------------
// HTTP API helpers
// ---------------------------------------------------------------------------

void MainWindow::apiGet(const QString &path)
{
    sendApiRequest("GET", path, QJsonObject());
}

void MainWindow::apiPost(const QString &path, const QJsonObject &body)
{
    sendApiRequest("POST", path, body);
}

void MainWindow::apiPatch(const QString &path, const QJsonObject &body)
{
    sendApiRequest("PATCH", path, body);
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
    if (method == "POST" || method == "PATCH") {
        appendLog("API BODY " + QString::fromUtf8(QJsonDocument(body).toJson(QJsonDocument::Compact)));
    }
    QNetworkReply *reply = nullptr;
    if (method == "POST") {
        reply = http.post(request, QJsonDocument(body).toJson(QJsonDocument::Compact));
    } else if (method == "PATCH") {
        reply = http.sendCustomRequest(request, "PATCH", QJsonDocument(body).toJson(QJsonDocument::Compact));
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

    if (label.startsWith("POST /api/messages/") || label.startsWith("PATCH /api/messages/")) {
        setActionStatus("消息操作已同步到服务端。");
        if (!currentConversationId.isEmpty()) {
            loadMessages(currentConversationId);
        }
        return;
    }

    // Route specific API responses
    if (label == "GET /api/conversations") {
        handleConversationsResponse(body);
        return;
    }
    if (label.startsWith("GET /api/conversations/") && label.contains("/messages")) {
        // Extract conversation id from path: GET /api/conversations/{id}/messages
        QString path = label;
        path.remove("GET /api/conversations/");
        int slashIdx = path.indexOf('/');
        QString convId = (slashIdx > 0) ? path.left(slashIdx) : path;
        handleMessagesResponse(convId, body);
        return;
    }
    if (label.startsWith("GET /api/files")) {
        handleFilesResponse(body);
        return;
    }
    if (label == "GET /api/directory/enterprises") {
        handleOrgEnterprisesResponse(body);
        return;
    }
    if (label.startsWith("GET /api/friends") || label.startsWith("GET /api/directory")) {
        handleContactsResponse(body);
        return;
    }
    if (label.startsWith("GET /api/friend-requests")) {
        handleFriendRequestsResponse(body);
        return;
    }
    if (label.startsWith("POST /api/friend-requests/")) {
        setActionStatus("好友申请已处理");
        loadFriendRequests();
        loadContacts();
        return;
    }
    if (label.startsWith("GET /api/users/")) {
        handleContactProfileResponse(body);
        return;
    }
    if (label.startsWith("GET /api/search")) {
        handleSearchResponse(body);
        return;
    }
    if (label.startsWith("GET /api/messages/") && label.endsWith("/read-status")) {
        handleReadStatusResponse(body);
        return;
    }
    if (label.startsWith("PATCH /api/notification-settings")) {
        setActionStatus("通知设置已同步到服务器");
        appendLog("通知设置同步成功");
        return;
    }
    if (label.startsWith("PATCH /api/conversations/") && label.contains("/settings")) {
        setActionStatus("会话设置已同步到服务器");
        appendLog("会话设置同步成功");
        return;
    }
    if (label == "POST /api/files/chunk-upload/sessions") {
        handleChunkSessionResponse(body);
        return;
    }
    if (label.startsWith("POST /api/files/chunk-upload/sessions/") && label.endsWith("/complete")) {
        handleFileUploadResponse(body);
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
            const bool mediaReady = object.value("mediaStatus").toString() == "media_ready";
            if (status == "answered" && mediaReady && nativeStartedCallId != activeCallId) {
                requestMediaConfig();
            }
            if (status == "rejected" || status == "ended") {
                sipMediaClient->stop();
                nativeStartedCallId.clear();
                stopLocalCameraPreview();
            }
        }

        if (object.contains("messageId")) {
            lastMessageId = object.value("messageId").toString();
            setActionStatus("消息操作成功，最近消息 ID=" + lastMessageId);
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

// ---------------------------------------------------------------------------
// SQLite local persistence
// ---------------------------------------------------------------------------

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
    query.exec("CREATE TABLE IF NOT EXISTS upload_queue (id INTEGER PRIMARY KEY AUTOINCREMENT, file_path TEXT NOT NULL, conversation_id TEXT, peer_id TEXT, retry_count INTEGER DEFAULT 0, status TEXT NOT NULL, last_error TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
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

void MainWindow::saveQueuedUpload(const QString &filePath, const QString &error)
{
    QSqlDatabase db = QSqlDatabase::database();
    if (!db.isOpen() || filePath.isEmpty()) return;
    QSqlQuery query(db);
    query.prepare("INSERT INTO upload_queue(file_path, conversation_id, peer_id, retry_count, status, last_error, created_at, updated_at) VALUES(?, ?, ?, 0, 'pending', ?, ?, ?)");
    const QString now = QDateTime::currentDateTime().toString(Qt::ISODate);
    query.addBindValue(filePath);
    query.addBindValue(currentConversationId.isEmpty() ? conversationIdEdit->text().trimmed() : currentConversationId);
    query.addBindValue(peerIdEdit->text().trimmed());
    query.addBindValue(error);
    query.addBindValue(now);
    query.addBindValue(now);
    query.exec();
    appendLog("UPLOAD QUEUE saved " + filePath);
}

void MainWindow::resumeQueuedUploads()
{
    if (uploadQueueResuming) return;
    QSqlDatabase db = QSqlDatabase::database();
    if (!db.isOpen()) return;
    QSqlQuery query(db);
    query.prepare("SELECT id, file_path, conversation_id, peer_id FROM upload_queue WHERE status = 'pending' ORDER BY id ASC LIMIT 5");
    if (!query.exec()) return;
    if (!query.next()) return;
    uploadQueueResuming = true;
    const int id = query.value(0).toInt();
    activeUploadQueueId = id;
    const QString filePath = query.value(1).toString();
    const QString convId = query.value(2).toString();
    const QString peerId = query.value(3).toString();
    if (!convId.isEmpty()) {
        currentConversationId = convId;
        conversationIdEdit->setText(convId);
    }
    if (!peerId.isEmpty()) peerIdEdit->setText(peerId);
    if (!QFileInfo::exists(filePath)) {
        deleteQueuedUpload(id);
        uploadQueueResuming = false;
        resumeQueuedUploads();
        return;
    }
    setActionStatus("正在恢复离线上传队列...");
    uploadFile(filePath);
}

void MainWindow::deleteQueuedUpload(int id)
{
    QSqlDatabase db = QSqlDatabase::database();
    if (!db.isOpen()) return;
    QSqlQuery query(db);
    query.prepare("DELETE FROM upload_queue WHERE id = ?");
    query.addBindValue(id);
    query.exec();
}

void MainWindow::loadFriendRequests()
{
    QUrlQuery query;
    query.addQueryItem("userId", userIdEdit->text().trimmed());
    query.addQueryItem("box", "incoming");
    apiGet("/api/friend-requests?" + query.toString());
}

void MainWindow::handleFriendRequestsResponse(const QByteArray &body)
{
    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject()) return;
    const QJsonObject root = doc.object();
    if (!root.value("success").toBool()) return;
    const QJsonArray items = root.value("data").toArray();
    friendRequestList->clear();
    for (int i = 0; i < items.size(); ++i) {
        const QJsonObject obj = items.at(i).toObject();
        const QString id = obj.value("id").toString();
        const QString name = obj.value("requesterName").toString(obj.value("senderName").toString("unknown"));
        const QString status = obj.value("status").toString();
        const QString message = obj.value("message").toString();
        auto *item = new QListWidgetItem(name + "  [" + status + "]\n" + message.left(60));
        item->setData(Qt::UserRole, id);
        friendRequestList->addItem(item);
    }
    setActionStatus("已加载好友申请：" + QString::number(items.size()) + " 条");
}

void MainWindow::acceptFriendRequest(const QString &requestId)
{
    QJsonObject body;
    body.insert("accept", true);
    apiPost("/api/friend-requests/" + requestId + "/handle", body);
}

void MainWindow::rejectFriendRequest(const QString &requestId)
{
    QJsonObject body;
    body.insert("accept", false);
    apiPost("/api/friend-requests/" + requestId + "/handle", body);
}

void MainWindow::showContactProfile(const QString &userId)
{
    if (userId.isEmpty()) return;
    apiGet("/api/users/" + userId);
}

void MainWindow::handleContactProfileResponse(const QByteArray &body)
{
    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject()) return;
    const QJsonObject root = doc.object();
    if (!root.value("success").toBool()) return;
    const QJsonObject user = root.value("data").toObject();
    const QString name = user.value("display_name").toString(user.value("displayName").toString(user.value("name").toString(user.value("id").toString())));
    const QString uid = user.value("id").toString();
    const QString phone = user.value("phone").toString();
    const QString signature = user.value("signature").toString();
    const QString email = user.value("email").toString();
    const QString online = user.value("online").toBool() ? "在线" : "离线";

    QDialog dlg(this);
    dlg.setWindowTitle("联系人资料");
    dlg.setMinimumWidth(380);
    auto *layout = new QVBoxLayout(&dlg);
    layout->setSpacing(12);

    // Avatar and name
    auto *headerLayout = new QHBoxLayout;
    auto *avatarLabel = new QLabel(avatarLetter(name));
    avatarLabel->setFixedSize(56, 56);
    avatarLabel->setAlignment(Qt::AlignCenter);
    avatarLabel->setStyleSheet(QString("background:%1;color:white;font-size:22px;font-weight:700;border-radius:28px;").arg(avatarColor(uid)));
    headerLayout->addWidget(avatarLabel);
    auto *nameLayout = new QVBoxLayout;
    nameLayout->addWidget(new QLabel("<b>" + name.toHtmlEscaped() + "</b>"));
    nameLayout->addWidget(new QLabel(online));
    headerLayout->addLayout(nameLayout);
    headerLayout->addStretch();
    layout->addLayout(headerLayout);

    // Info
    auto *infoGroup = new QGroupBox("详细信息");
    auto *infoLayout = new QFormLayout;
    infoLayout->setLabelAlignment(Qt::AlignLeft);
    infoLayout->addRow("ID：", new QLabel(uid));
    if (!phone.isEmpty()) infoLayout->addRow("手机：", new QLabel(phone));
    if (!email.isEmpty()) infoLayout->addRow("邮箱：", new QLabel(email));
    if (!signature.isEmpty()) infoLayout->addRow("签名：", new QLabel(signature));
    infoGroup->setLayout(infoLayout);
    layout->addWidget(infoGroup);

    // Action buttons
    auto *actionLayout = new QVBoxLayout;
    actionLayout->setSpacing(8);
    auto *sendMsgBtn = new QPushButton("发送消息");
    sendMsgBtn->setObjectName("primaryButton");
    auto *voiceCallBtn = new QPushButton("语音通话");
    auto *videoCallBtn = new QPushButton("视频通话");
    auto *deleteFriendBtn = new QPushButton("删除好友");
    deleteFriendBtn->setObjectName("dangerButton");

    actionLayout->addWidget(sendMsgBtn);
    actionLayout->addWidget(voiceCallBtn);
    actionLayout->addWidget(videoCallBtn);
    actionLayout->addWidget(deleteFriendBtn);
    layout->addLayout(actionLayout);

    // Close button
    auto *closeBtn = new QPushButton("关闭");
    layout->addWidget(closeBtn);

    // Connect actions
    connect(sendMsgBtn, &QPushButton::clicked, &dlg, [this, uid, name, &dlg]() {
        // Find or create single conversation with this user
        for (int i = 0; i < conversations.size(); ++i) {
            if (conversations[i].type == "single" && conversations[i].targetId == uid) {
                currentConversationId = conversations[i].id;
                conversationIdEdit->setText(conversations[i].id);
                peerIdEdit->setText(uid);
                chatTitleLabel->setText(name);
                loadMessages(conversations[i].id);
                leftTabs->setCurrentWidget(conversationList);
                dlg.close();
                return;
            }
        }
        // Create new conversation
        currentConversationId = "single_" + uid;
        conversationIdEdit->setText(currentConversationId);
        peerIdEdit->setText(uid);
        chatTitleLabel->setText(name);
        loadMessages(currentConversationId);
        leftTabs->setCurrentWidget(conversationList);
        dlg.close();
    });
    connect(voiceCallBtn, &QPushButton::clicked, &dlg, [this, uid]() {
        peerIdEdit->setText(uid);
        startAudioCall();
    });
    connect(videoCallBtn, &QPushButton::clicked, &dlg, [this, uid]() {
        peerIdEdit->setText(uid);
        startVideoCall();
    });
    connect(deleteFriendBtn, &QPushButton::clicked, &dlg, [this, uid, &dlg]() {
        if (QMessageBox::question(&dlg, "删除好友", "确定删除该好友吗？") == QMessageBox::Yes) {
            const QString token = tokenEdit->text().trimmed();
            QNetworkRequest req(QUrl(baseUrl() + "/api/friends/" + QUrl::toPercentEncoding(uid)));
            req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
            if (!token.isEmpty()) req.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
            QNetworkReply *reply = http.deleteResource(req);
            connect(reply, &QNetworkReply::finished, this, [this, reply]() {
                reply->deleteLater();
                setActionStatus("好友已删除");
                loadContacts();
            });
            dlg.close();
        }
    });
    connect(closeBtn, &QPushButton::clicked, &dlg, &QDialog::close);

    dlg.exec();
}

void MainWindow::performSearch(const QString &filterType)
{
    const QString keyword = searchEdit->text().trimmed();
    if (keyword.isEmpty()) {
        setActionStatus("请输入搜索关键词", true);
        return;
    }
    QUrlQuery query;
    query.addQueryItem("q", keyword);
    query.addQueryItem("type", filterType.isEmpty() ? "all" : filterType);
    apiGet("/api/search?" + query.toString());
}

void MainWindow::handleSearchResponse(const QByteArray &body)
{
    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject()) return;
    const QJsonObject root = doc.object();
    if (!root.value("success").toBool()) return;
    const QJsonObject data = root.value("data").toObject();
    searchResultList->clear();

    const QString keyword = searchEdit->text().trimmed();

    auto addItems = [this, &keyword](const QString &kind, const QJsonArray &items) {
        for (int i = 0; i < items.size(); ++i) {
            const QJsonObject obj = items.at(i).toObject();
            QString id = obj.value("id").toString();
            if (kind == "message") {
                id = obj.value("conversationId").toString(id);
            }
            QString title = obj.value("name").toString(obj.value("content").toString(obj.value("originalName").toString(id)));
            // Highlight matching keyword
            if (!keyword.isEmpty() && title.contains(keyword, Qt::CaseInsensitive)) {
                const QRegularExpression re(QRegularExpression::escape(keyword), QRegularExpression::CaseInsensitiveOption);
                title.replace(re, "<b><font color='#0066FF'>" + keyword.toHtmlEscaped() + "</font></b>");
            } else {
                title = title.left(80).toHtmlEscaped();
            }
            auto *item = new QListWidgetItem(kind + ": " + title);
            item->setData(Qt::UserRole, id);
            item->setData(Qt::UserRole + 1, kind);
            searchResultList->addItem(item);
        }
    };

    addItems("contact", data.value("contacts").toArray());
    addItems("group", data.value("groups").toArray());
    addItems("message", data.value("messages").toArray());
    addItems("file", data.value("files").toArray());
    setActionStatus("搜索完成：" + QString::number(searchResultList->count()) + " 条");
}

void MainWindow::handleReadStatusResponse(const QByteArray &body)
{
    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject()) {
        setActionStatus("已读明细返回格式错误", true);
        return;
    }
    const QJsonObject root = doc.object();
    if (!root.value("success").toBool()) {
        setActionStatus("已读明细读取失败：" + root.value("error").toString(), true);
        return;
    }
    const QJsonObject data = root.value("data").toObject();
    const QJsonArray read = data.value("read").toArray();
    const QJsonArray unread = data.value("unread").toArray();
    QString text = QString("已读 %1 人\n").arg(read.size());
    for (const QJsonValue &value : read) {
        const QJsonObject item = value.toObject();
        text += "  - " + item.value("displayName").toString(item.value("userId").toString());
        const QString readAt = item.value("readAt").toString();
        if (!readAt.isEmpty()) text += "  " + readAt.left(19);
        text += "\n";
    }
    text += QString("\n未读 %1 人\n").arg(unread.size());
    for (const QJsonValue &value : unread) {
        const QJsonObject item = value.toObject();
        text += "  - " + item.value("displayName").toString(item.value("userId").toString()) + "\n";
    }
    QMessageBox::information(this, "已读明细", text);
    setActionStatus("已读明细读取完成");
}

void MainWindow::onSearchResultClicked(QListWidgetItem *item)
{
    if (!item) return;
    const QString kind = item->data(Qt::UserRole + 1).toString();
    const QString id = item->data(Qt::UserRole).toString();
    if (id.isEmpty()) return;
    if (kind == "contact") {
        showContactProfile(id);
        return;
    }
    if (kind == "group" || kind == "message") {
        currentConversationId = id;
        conversationIdEdit->setText(id);
        loadMessages(id);
        leftTabs->setCurrentWidget(conversationList);
        return;
    }
    setActionStatus("已选中搜索结果：" + id);
}

// ---------------------------------------------------------------------------
// File management tab
// ---------------------------------------------------------------------------

void MainWindow::loadFiles()
{
    const QString userId = userIdEdit->text().trimmed();
    apiGet("/api/files?userId=" + QUrl::toPercentEncoding(userId) + "&limit=100");
}

void MainWindow::handleFilesResponse(const QByteArray &body)
{
    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject()) return;
    const QJsonObject root = doc.object();
    if (!root.value("success").toBool()) return;
    const QJsonArray items = root.value("data").toObject().value("items").toArray();
    fileList->clear();
    for (int i = 0; i < items.size(); ++i) {
        const QJsonObject file = items.at(i).toObject();
        const QString id = file.value("id").toString();
        const QString name = file.value("originalName").toString(file.value("original_name").toString("unknown"));
        const qint64 size = static_cast<qint64>(file.value("sizeBytes").toDouble(file.value("size_bytes").toDouble(0)));
        const QString contentType = file.value("contentType").toString(file.value("content_type").toString());
        QString sizeStr;
        if (size >= 1073741824) sizeStr = QString::number(size / 1073741824.0, 'f', 1) + " GB";
        else if (size >= 1048576) sizeStr = QString::number(size / 1048576.0, 'f', 1) + " MB";
        else if (size >= 1024) sizeStr = QString::number(size / 1024.0, 'f', 1) + " KB";
        else sizeStr = QString::number(size) + " B";
        QString icon;
        if (contentType.startsWith("image/")) icon = QString::fromUtf8("\xf0\x9f\x96\xbc ");
        else if (contentType.startsWith("video/")) icon = QString::fromUtf8("\xf0\x9f\x8e\xac ");
        else if (contentType.startsWith("audio/")) icon = QString::fromUtf8("\xf0\x9f\x8e\xb5 ");
        else if (contentType.contains("pdf")) icon = QString::fromUtf8("\xf0\x9f\x93\x95 ");
        else icon = QString::fromUtf8("\xf0\x9f\x93\x84 ");
        auto *item = new QListWidgetItem(icon + name + "  (" + sizeStr + ")");
        item->setData(Qt::UserRole, id);
        fileList->addItem(item);
    }
    setActionStatus("已加载文件：" + QString::number(items.size()) + " 个");
}

// ---------------------------------------------------------------------------
// Organization tab
// ---------------------------------------------------------------------------

void MainWindow::loadOrganization()
{
    apiGet("/api/directory/enterprises");
}

void MainWindow::handleOrgEnterprisesResponse(const QByteArray &body)
{
    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isObject()) return;
    const QJsonObject root = doc.object();
    if (!root.value("success").toBool()) return;
    QJsonArray enterprises = root.value("data").toObject().value("items").toArray();

    // Store enterprises and chain to departments
    // Capture enterprises by value for later tree building
    const QString token = tokenEdit->text().trimmed();
    const QString base = baseUrl();

    QUrl deptUrl(base + "/api/directory/departments");
    QNetworkRequest deptReq(deptUrl);
    deptReq.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    if (!token.isEmpty()) deptReq.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
    QNetworkReply *deptReply = http.get(deptReq);
    connect(deptReply, &QNetworkReply::finished, this, [this, deptReply, enterprises, token, base]() {
        const QByteArray deptBody = deptReply->readAll();
        deptReply->deleteLater();
        const QJsonDocument deptDoc = QJsonDocument::fromJson(deptBody);
        QJsonArray departments;
        if (deptDoc.isObject() && deptDoc.object().value("success").toBool()) {
            departments = deptDoc.object().value("data").toObject().value("items").toArray();
        }

        // Chain to users
        QUrl userUrl(base + "/api/directory/users");
        QNetworkRequest userReq(userUrl);
        userReq.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
        if (!token.isEmpty()) userReq.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
        QNetworkReply *userReply = http.get(userReq);
        connect(userReply, &QNetworkReply::finished, this, [this, userReply, enterprises, departments]() {
            const QByteArray userBody = userReply->readAll();
            userReply->deleteLater();
            const QJsonDocument userDoc = QJsonDocument::fromJson(userBody);
            QJsonArray users;
            if (userDoc.isObject() && userDoc.object().value("success").toBool()) {
                users = userDoc.object().value("data").toObject().value("items").toArray();
            }

            // Build tree: enterprise → department → user
            orgTree->clear();
            for (int ei = 0; ei < enterprises.size(); ++ei) {
                const QJsonObject ent = enterprises.at(ei).toObject();
                const QString eid = ent.value("id").toString();
                const QString ename = ent.value("name").toString(eid);
                auto *entItem = new QTreeWidgetItem(orgTree);
                entItem->setText(0, ename);
                entItem->setText(1, "企业");
                entItem->setData(0, Qt::UserRole, eid);
                entItem->setData(0, Qt::UserRole + 1, "enterprise");

                for (int di = 0; di < departments.size(); ++di) {
                    const QJsonObject dept = departments.at(di).toObject();
                    if (dept.value("enterprise_id").toString(dept.value("enterpriseId").toString()) != eid) continue;
                    const QString did = dept.value("id").toString();
                    const QString dname = dept.value("name").toString(did);
                    auto *deptItem = new QTreeWidgetItem(entItem);
                    deptItem->setText(0, dname);
                    deptItem->setText(1, "部门");
                    deptItem->setData(0, Qt::UserRole, did);
                    deptItem->setData(0, Qt::UserRole + 1, "department");

                    for (int ui = 0; ui < users.size(); ++ui) {
                        const QJsonObject user = users.at(ui).toObject();
                        const QString userDeptId = user.value("department_id").toString(user.value("departmentId").toString());
                        const QString userEntId = user.value("enterprise_id").toString(user.value("enterpriseId").toString());
                        if (userDeptId != did && userEntId != eid) continue;
                        const QString uid = user.value("id").toString();
                        const QString uname = user.value("display_name").toString(user.value("displayName").toString(user.value("name").toString(uid)));
                        auto *userItem = new QTreeWidgetItem(deptItem);
                        userItem->setText(0, uname);
                        userItem->setText(1, uid);
                        userItem->setData(0, Qt::UserRole, uid);
                        userItem->setData(0, Qt::UserRole + 1, "user");
                    }
                }
            }
            orgTree->expandAll();
            setActionStatus("组织架构已加载");
        });
    });
}

void MainWindow::onOrgTreeItemDoubleClicked(QTreeWidgetItem *item, int column)
{
    Q_UNUSED(column);
    if (!item) return;
    const QString kind = item->data(0, Qt::UserRole + 1).toString();
    const QString id = item->data(0, Qt::UserRole).toString();
    if (kind == "user" && !id.isEmpty()) {
        showContactProfile(id);
    }
}
