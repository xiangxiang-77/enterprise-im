#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QByteArray>
#include <QJsonObject>
#include <QMainWindow>
#include <QMap>
#include <QNetworkAccessManager>
#include <QSettings>
#include <QSet>
#include <QTcpSocket>
#include <QVector>

class SipMediaClient;
class QLineEdit;
class QLabel;
class QListWidget;
class QListWidgetItem;
class QPushButton;
class QTreeWidget;
class QTreeWidgetItem;
class QTextBrowser;
class QTextEdit;
class QWidget;
class QStackedWidget;
class QTabWidget;
class QFileDialog;
class QTimer;

#ifdef ENTERPRISE_IM_HAS_MULTIMEDIA
class QCamera;
class QCameraViewfinder;
#endif

struct ConversationItem
{
    QString id;
    QString name;
    QString type;
    QString targetId;
    QString lastMessage;
    QString timestamp;
    int unreadCount;
    bool muted;
    bool pinned;
    bool screenshotNotice;
    bool recallNotice;
    bool readAfterBurn;
    bool strongReminder;
    bool displayMemberNicknames;
    bool savedToContacts;
};

struct ContactItem
{
    QString id;
    QString name;
    QString avatarLetter;
    bool online;
};

class MainWindow : public QMainWindow
{
    Q_OBJECT

public:
    explicit MainWindow(QWidget *parent = nullptr);

protected:
    void closeEvent(QCloseEvent *event) override;

private:
    // --- Login panel ---
    QLineEdit *phoneEdit;
    QLineEdit *passwordEdit;
    QPushButton *loginButton;
    QLabel *loginStatusLabel;
    QWidget *loginPanel;

    // --- Session / user state ---
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
    QLabel *chatTitleLabel;

    // --- Left panel ---
    QStackedWidget *leftStack;
    QTabWidget *leftTabs;
    QListWidget *conversationList;
    QListWidget *contactList;
    QLabel *contactCountLabel;
    QListWidget *friendRequestList;
    QListWidget *fileList;
    QTreeWidget *orgTree;
    QLineEdit *searchEdit;
    QListWidget *searchResultList;
    QPushButton *logoutButton;
    QPushButton *settingsButton;

    // --- Chat area ---
    QTextBrowser *chatBrowser;
    QPushButton *sendButton;
    QPushButton *fileButton;
    QPushButton *voiceRecordButton;
    QPushButton *cardButton;
    QPushButton *imageEditButton;
    QPushButton *forwardSelectedButton;
    bool voiceRecording;
    QString voiceRecordFilePath;
    void *voiceRecordCtx;
    QPushButton *favoriteButton;
    QPushButton *likeButton;
    QPushButton *recallButton;
    QPushButton *editButton;
    QWidget *composerPanel;

    // --- Call controls ---
    QPushButton *connectButton;
    QPushButton *authButton;
    QPushButton *pingButton;
    QPushButton *readinessButton;
    QPushButton *audioButton;
    QPushButton *videoButton;
    QPushButton *answerButton;
    QPushButton *rejectButton;
    QPushButton *hangupButton;
    QPushButton *historyButton;
    QPushButton *debugToggleButton;
    QWidget *callPanel;
    QWidget *callScreen;
    QLabel *callScreenTitle;
    QLabel *callScreenState;
    QLabel *callScreenPeer;
    QLabel *callScreenAvatar;
    QLabel *callScreenHint;
    QPushButton *callScreenAnswerButton;
    QPushButton *callScreenRejectButton;
    QPushButton *callScreenHangupButton;
    QTextEdit *eventView;

#ifdef ENTERPRISE_IM_HAS_MULTIMEDIA
    QCamera *camera;
    QCameraViewfinder *cameraViewfinder;
#endif

    // --- Network / state ---
    QTcpSocket socket;
    QNetworkAccessManager http;
    QByteArray pending;
    QString activeCallId;
    QString activeMediaType;
    QString activeCallStatus;
    bool activeCallIncoming;
    QString nativeStartedCallId;
    QString lastMessageId;
    QString chunkUploadFilePath;
    QString chunkUploadSessionId;
    QString chunkUploadOriginalName;
    QString chunkUploadFileUrl;
    QString chunkUploadFileId;
    qint64 chunkUploadTotalBytes;
    int chunkUploadTotalChunks;
    int chunkUploadNextChunk;
    int chunkUploadRetryCount;
    int activeUploadQueueId;
    bool uploadQueueResuming;
    SipMediaClient *sipMediaClient;
    bool debugVisible;
    bool darkMode;
    bool loggedIn;
    bool closing;
    int reconnectAttempts;
    QTimer *reconnectTimer;
    QTimer *typingTimer;
    QString typingPeerName;
    QTimer *groupOnlineTimer;

    // --- Data ---
    QVector<ConversationItem> conversations;
    QVector<ContactItem> contacts;
    QString currentConversationId;
    QMap<QString, QString> messageContentById;
    QSet<QString> selectedMessageIds;
    QSettings settings;

    // --- Existing methods ---
    void connectToServer();
    void sendAuth();
    void sendPing();
    void sendText();
    void favoriteLastMessage();
    void likeLastMessage();
    void recallLastMessage();
    void editLastMessage();
    void handleMessageActionLink(const QUrl &url);
    void favoriteMessage(const QString &messageId);
    void likeMessage(const QString &messageId);
    void recallMessage(const QString &messageId);
    void editMessage(const QString &messageId);
    void showReadStatus(const QString &messageId);
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
    void scheduleReconnect();
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
    void apiPatch(const QString &path, const QJsonObject &body);
    void sendApiRequest(const QString &method, const QString &path, const QJsonObject &body);
    void handleApiResponse(const QString &label, const QByteArray &body);
    QString baseUrl() const;

    // --- New methods ---
    void doLogin();
    void doLogout();
    void handleLoginResponse(const QByteArray &body);
    void showLoggedInUI();
    void showLoginUI();
    void loadConversations();
    void handleConversationsResponse(const QByteArray &body);
    void loadContacts();
    void handleContactsResponse(const QByteArray &body);
    void loadMessages(const QString &conversationId);
    void handleMessagesResponse(const QString &conversationId, const QByteArray &body);
    void onConversationClicked(QListWidgetItem *item);
    void onContactClicked(QListWidgetItem *item);
    void pickAndSendFile();
    void pickEditAndSendImage();
    void toggleVoiceRecording();
    void playVoice(const QString &fileUrl);
    void uploadFile(const QString &filePath);
    void handleFileUploadResponse(const QByteArray &body);
    void startChunkUpload(const QString &filePath);
    void handleChunkSessionResponse(const QByteArray &body);
    void uploadNextChunk();
    void completeChunkUpload();
    void saveQueuedUpload(const QString &filePath, const QString &error);
    void resumeQueuedUploads();
    void deleteQueuedUpload(int id);
    void appendChatBubble(const QString &sender, const QString &content, const QString &timestamp, bool isSelf);
    void appendChatBubble(const QString &sender, const QString &content, const QString &timestamp, bool isSelf, const QString &msgType, const QString &fileUrl, const QString &fileName, qint64 fileSize);
    QString avatarColor(const QString &userId) const;
    QString avatarLetter(const QString &name) const;
    void saveSettings();
    void loadSettings();
    void sendTextMessage(const QString &conversationId, const QString &to, const QString &content);
    void sendTyping(bool isTyping);
    void clearTypingIndicator();
    void pollGroupOnlineStatus();
    void applyTheme();
    void showSettingsDialog();
    void showConversationSettingsDialog();
    void onConversationContextMenu(const QPoint &pos);
    void sendCardMessage();
    void showImageViewer(const QStringList &imageUrls, int startIndex = 0);
    void showAtMentionPicker();
    bool isGroupConversation(const QString &conversationId) const;
    ConversationItem *currentConversation();
    QString resolveFileUrl(const QString &fileUrl) const;

    // --- Round 3: friend requests, profile, search, notifications ---
    void loadFriendRequests();
    void handleFriendRequestsResponse(const QByteArray &body);
    void acceptFriendRequest(const QString &requestId);
    void rejectFriendRequest(const QString &requestId);
    void showContactProfile(const QString &userId);
    void handleContactProfileResponse(const QByteArray &body);
    void performSearch(const QString &filterType = "all");
    void handleSearchResponse(const QByteArray &body);
    void handleReadStatusResponse(const QByteArray &body);
    void onSearchResultClicked(QListWidgetItem *item);
    void loadFiles();
    void handleFilesResponse(const QByteArray &body);
    void loadOrganization();
    void handleOrgEnterprisesResponse(const QByteArray &body);
    void onOrgTreeItemDoubleClicked(QTreeWidgetItem *item, int column);
    void toggleMessageSelection(const QString &messageId);
    void forwardSelectedMessages();
    void forwardMessages(const QStringList &messageIds);
};

#endif
