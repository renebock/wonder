package er.javamail;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.webobjects.eocontrol.EOEnterpriseObject;
import com.webobjects.eocontrol.EOOrQualifier;
import com.webobjects.eocontrol.EOQualifier;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSMutableArray;

import er.extensions.ERXExtensions;
import er.extensions.ERXFrameworkPrincipal;
import er.extensions.foundation.ERXProperties;
import er.extensions.validation.ERXValidationFactory;

/**
 * ERJavaMail is the principal class for the ERJavaMail framework.
 * 
 * @property er.javamail.centralize
 * @property er.javamail.adminEmail
 * @property er.javamail.debugEnabled
 * @property er.javamail.senderQueue.size
 * @property er.javamail.milliSecondsWaitIfSenderOverflowed
 * @property er.javamail.XMailerHeader
 * @property er.javamail.smtpProtocol
 * @property er.javamail.smtpHost
 * @property mail.[smtpProtocol].host
 * @property WOSMTPHost
 * @property er.javamail.smtpPort
 * @property mail.[smtpProtocol].port
 * @property er.javamail.smtpAuth
 * @property mail.[smtpProtocol].auth
 * @property er.javamail.smtpUser
 * @property mail.[smtpProtocol].user
 * @property er.javamail.smtpPassword
 * @property mail.[smtpProtocol].password
 * @property mail.smtps.socketFactory.fallback
 * @property er.javamail.emailPattern
 * @property er.javamail.AllowEmailAddressPatterns
 * @property er.javamail.DenyEmailAddressPatterns
 * @property er.javamail.sessionConfigViaJNDI
 * @property er.javamail.jndiSessionContext
 * 
 * @author <a href="mailto:tuscland@mac.com">Camille Troillard</a>
 * @author <a href="mailto:maxmuller@mac.com">Max Muller</a>
 */
public class ERJavaMail extends ERXFrameworkPrincipal {
	public final static Class<?> REQUIRES[] = new Class[] { ERXExtensions.class };

	static {
		setUpFrameworkPrincipalClass(ERJavaMail.class);
	}

	/*
	 * Lazy initialisation, see Effective Java, Item 71
	 */
	private static class SharedInstanceHolder {
		private static final ERJavaMail singelton = ERXFrameworkPrincipal.sharedInstance(ERJavaMail.class);
	}

	/**
	 * <span class="en"> Accessor to the ERJavaMail singleton.
	 * 
	 * @return the one <code>ERJavaMail</code> instance </span>
	 * 
	 *         <span class="ja"> ERJavaMail シングルトン・アクセス・メソッド
	 * 
	 * @return <code>ERJavaMail</code> インスタンス </span>
	 */
	public static ERJavaMail sharedInstance() {
		return SharedInstanceHolder.singelton;
	}

	/**
	 * <span class="en"> <code>EMAIL_VALIDATION_PATTERN</code> is a regexp pattern that is used to validate
	 * emails. </span>
	 * 
	 * <span class="ja"> <code>EMAIL_VALIDATION_PATTERN</code> はメールアドレスの検証のための Regex パタン </span>
	 */
	// RFC 2822 token definitions for valid email - only used together to form a java Pattern object:
	private static final String sp = "!#$%&'*+\\-/=?^_`{|}~";
	private static final String atext = "[a-zA-Z0-9" + sp + "]";
	private static final String atom = atext + "+"; // one or more atext chars
	private static final String dotAtom = "\\." + atom;
	private static final String localPart = atom + "(" + dotAtom + ")*"; // one atom followed by 0 or more dotAtoms.
	// RFC 1035 tokens for domain names:
	private static final String letter = "[a-zA-Z]";
	private static final String letDig = "[a-zA-Z0-9]";
	private static final String letDigHyp = "[a-zA-Z0-9\\-]";
	private static final String rfcLabel = letDig + "(" + letDigHyp + "{0,61}" + letDig + "){0,1}";
	private static final String domain = rfcLabel + "((\\." + rfcLabel + ")*\\." + letter + "{2,6}){0,1}";
	// Combined together, these form the allowed email regexp allowed by RFC 2822:
	private static final String EMAIL_VALIDATION_PATTERN = "^" + localPart + "@" + domain + "$";

	/**
	 * The compiled form of the <code>EMAIL_VALIDATION_PATTERN</code> pattern.
	 */
	protected Pattern _pattern = null;

	private Delegate _delegate;

	public void setDelegate(Delegate delegate) {
		_delegate = delegate;
	}

	/**
	 * Specialized implementation of the method from ERXPrincipalClass.
	 */
	@Override
	public void finishInitialization() {
		initializeFrameworkFromSystemProperties();
		log.debug("ERJavaMail loaded");
	}

	/**
	 * <span class="en"> This method is used to initialize ERJavaMail from System properties. Later, we will implement a
	 * way to initialize those properties everytime the propertis are changed. The observer will call this method
	 * whenever appropriate. </span>
	 * 
	 * <span class="ja"> このメソッドは ERJavaMail をシステム・プロパティより初期化するためにあります。 後でプロパティが変更される度にこのメソッドが実行される処理を追加実装します。 </span>
	 */
	public void initializeFrameworkFromSystemProperties() {
		// Centralize mails ?
		boolean centralize = ERXProperties.booleanForKey("er.javamail.centralize");
		setCentralize(centralize);
		log.debug("er.javamail.centralize: " + centralize);

		String adminEmail = System.getProperty("er.javamail.adminEmail");
		if (isValidEmail(adminEmail)) {
			setAdminEmail(adminEmail);
			log.debug("er.javamail.adminEmail: " + _adminEmail);
		}
		else if (centralize) {
			throw new IllegalArgumentException("When 'er.javamail.centralize' is true (default)," + " all outgoing mails will get sent to 'er.javamail.adminEmail'" + " instead of the normal TO addresses, but you did not provide a valid email for that property.");
		}

		// JavaMail Debug Enabled ?
		boolean debug = ERXProperties.booleanForKey("er.javamail.debugEnabled");
		setDebugEnabled(debug);
		log.debug("er.javamail.debugEnabled: " + debug);

		// Number of messages that the sender queue can hold at a time
		int queueSize = ERXProperties.intForKey("er.javamail.senderQueue.size");
		if (queueSize >= 1)
			setSenderQueueSize(queueSize);
		log.debug("er.javamail.senderQueue.size: " + queueSize);

		// Time to wait when sender if overflowed
		int milliswait = ERXProperties.intForKey("er.javamail.milliSecondsWaitIfSenderOverflowed");
		if (milliswait > 1000)
			setMilliSecondsWaitIfSenderOverflowed(milliswait);
		log.debug("er.javamail.milliSecondsWaitIfSenderOverflowed: " + milliswait);

		// Smtp host
		setupSmtpHostSafely();

		// Default X-Mailer header
		setDefaultXMailerHeader(System.getProperty("er.javamail.XMailerHeader"));
		log.debug("er.javamail.XMailHeader: " + defaultXMailerHeader());
	}

	/**
	 * <span class="en"> Helper method to init the smtpHost property. This method first check is
	 * <code>er.javamail.smtpHost</code> is set. If it is not set, then it looks for <code>mail.smtp.host</code>
	 * (standard JavaMail property) and finally the <code>WOSMTPHost</code> property. When a correct property is found,
	 * then it sets both properties to the found value. If no properties are found, a RuntimeException is thrown.
	 * 
	 * @throws RuntimeException
	 *             if neither one of <code>er.javamail.smtpHost</code>, <code>mail.smtp.host</code> or
	 *             <code>WOSMTPHost</code> is set. </span>
	 * 
	 *             <span class="ja"> smtpHost プロパティを初期化するヘルプ・メソッドです。 最初には <code>er.javamail.smtpHost</code>
	 *             がセットされているかどうかをチェックします。 セットされていなければ、<code>mail.smtp.host</code> (標準 JavaMail プロパティ) をチェックし、最終的には
	 *             <code>WOSMTPHost</code> プロパティ。 正しいプロパティが見つかると結果値を両方のプロパティにセットします。見つからない場合には RuntimeException が発生します。
	 * 
	 * @throws RuntimeException
	 *             - <code>er.javamail.smtpHost</code>, <code>mail.smtp.host</code> 又は <code>WOSMTPHost</code>
	 *             がセットされていなければ </span>
	 */
	protected void setupSmtpHostSafely() {
		setupSmtpProperties(System.getProperties(), null);
	}

	protected void setupSmtpProperties(Properties properties, String contextString) {
		String contextSuffix = contextString == null ? "" : ("." + contextString);

		// Smtp host
		String smtpProtocol = smtpProtocolForContext(contextString);

		String smtpHost = ERXProperties.stringForKeyWithDefault("er.javamail.smtpHost" + contextSuffix, ERXProperties.stringForKey("er.javamail.smtpHost"));
		if ((smtpHost == null) || (smtpHost.length() == 0)) {
			// Try to fail back to default java config
			smtpHost = ERXProperties.stringForKey("mail." + smtpProtocol + ".host");

			if ((smtpHost == null) || (smtpHost.length() == 0)) {
				// use the standard WO host
				smtpHost = ERXProperties.stringForKey("WOSMTPHost");
				if ((smtpHost == null) || (smtpHost.length() == 0)) {
					throw new RuntimeException("ERJavaMail: You must specify a SMTP host for outgoing mail with the property 'er.javamail.smtpHost'");
				}
				// ... and then maybe actually do what the docs say this method is supposed to do
				properties.setProperty("mail." + smtpProtocol + ".host", smtpHost);
				properties.setProperty("er.javamail.smtpHost", smtpHost);
			}
			else {
				properties.setProperty("er.javamail.smtpHost", smtpHost);
			}
		}
		else {
			properties.setProperty("mail." + smtpProtocol + ".host", smtpHost);
		}
		log.debug("er.javamail.smtpHost: " + smtpHost);

		String port = ERXProperties.stringForKeyWithDefault("er.javamail.smtpPort" + contextSuffix, ERXProperties.stringForKey("er.javamail.smtpPort"));
		if (port != null && port.length() > 0) {
			properties.setProperty("mail." + smtpProtocol + ".port", port);
			log.debug("ERJavaMail will use smtp port: " + port);
		}

		boolean smtpAuth = ERXProperties.booleanForKeyWithDefault("er.javamail.smtpAuth" + contextSuffix, ERXProperties.booleanForKey("er.javamail.smtpAuth"));
		log.debug("ERJavaMail will use authenticated SMTP connections.");
		if (smtpAuth) {
			properties.setProperty("mail." + smtpProtocol + ".auth", String.valueOf(smtpAuth));
			String user = ERXProperties.stringForKeyWithDefault("er.javamail.smtpUser" + contextSuffix, ERXProperties.stringForKey("er.javamail.smtpUser"));
			if (user == null || user.length() == 0) {
				throw new RuntimeException("You specified er.javamail.smtpAuth=true, but you didn't specify an er.javamail.smtpUser to use as the login name.");
			}
			properties.setProperty("mail." + smtpProtocol + ".user", user);
			String password = ERXProperties.stringForKeyWithDefault("er.javamail.smtpPassword" + contextSuffix, ERXProperties.stringForKey("er.javamail.smtpPassword"));
			if (password == null || password.length() == 0) {
				log.warn("You specified er.javamail.smtpAuth=true, but you didn't set er.javamail.smtpPassword for the " + user + " mail user.");
			}
			if (password != null) {
				properties.setProperty("mail." + smtpProtocol + ".password", password);
			}
		}
		if ("smtps".equals(smtpProtocol)) {
			properties.setProperty("mail.smtps.socketFactory.fallback", "false");
		}
	}

	/**
	 * <span class="en"> This is the default JavaMail Session. It is shared among all deliverers for immediate
	 * deliveries. Deferred deliverers, use their own JavaMail session. </span>
	 * 
	 * <span class="ja"> JavaMail のデフォルト・セッションです。 即時配信処理より共有されています。 延期配信は独自の JavaMail セッションを使用しています。 </span>
	 */
	protected volatile javax.mail.Session _defaultSession;
	private final Map<String, javax.mail.Session> _sessions = new ConcurrentHashMap<>();

	/**
	 * <span class="en"> Sets the default JavaMail session to a particular value. This value is set by default at
	 * initialization of the framework but you can specify a custom one by using this method. Note that a new deliverer
	 * need to be instanciated for changes to be taken in account.
	 * 
	 * @param session
	 *            the default <code>javax.mail.Session</code> </span>
	 * 
	 *            <span class="ja"> JavaMail のデフォルト・セッションをセットします。 フレームワークの初期化時に設定されのですが、独自で設定する時には ここを実行するといいのです。
	 * 
	 * @param session
	 *            - デフォルト <code>javax.mail.Session</code> </span>
	 */
	public void setDefaultSession(javax.mail.Session session) {
		session.setDebug(debugEnabled());
		_defaultSession = session;
	}

	/**
	 * <span class="en"> This is the deafult JavaMail Session accessor. It is shared among all deliverers for immediate
	 * deliveries. Deferred deliverers, use their own JavaMail session.
	 * 
	 * @return the default <code>javax.mail.Session</code> instance </span>
	 * 
	 *         <span class="ja"> JavaMail のデフォルト・セッション・アクセス方法です。 即時配信処理のために共有されています。 延期配信は独自の JavaMail セッションを使用しています。
	 * 
	 * @return デフォルト <code>javax.mail.Session</code> インスタンス </span>
	 */
	public javax.mail.Session defaultSession() {

		if(_defaultSession == null) {
			synchronized (this) {
				if(_defaultSession == null) {
					setDefaultSession(newSession());					
				}
			}
		}

		return _defaultSession;
	}

	/**
	 * <span class="en"> Returns a newly allocated Session object from the given Properties
	 * 
	 * @param props
	 *            a <code>Properties</code> value
	 * @return a <code>javax.mail.Session</code> value initialized from the given properties </span>
	 * 
	 *         <span class="ja"> 指定プロパティを使った新規セッションを戻します。
	 * 
	 * @param props
	 *            - <code>Properties</code> 値
	 * 
	 * @return 指定プロパティで初期化されている <code>javax.mail.Session</code> 値 </span>
	 */
	public javax.mail.Session newSession(Properties props) {
		return newSessionForContext(props, null);
	}

	/**
	 * <span class="en"> Returns a newly allocated Session object from the System Properties
	 * 
	 * @return a <code>javax.mail.Session</code> value </span>
	 * 
	 *         <span class="ja"> システム・プロパティを使った新規セッションを戻します。
	 * 
	 * @return <code>javax.mail.Session</code> 値 </span>
	 */
	public javax.mail.Session newSession() {
		return newSessionForContext(null);
	}

	/**
	 * Returns a newly allocated Session object for the given message.
	 * 
	 * @param message
	 *            the message
	 * @return a new <code>javax.mail.Session</code> value
	 */
	public javax.mail.Session newSessionForMessage(ERMessage message) {
		return newSessionForContext(message.contextString());
	}

	/**
	 * Returns the Session object that is appropriate for the given message.
	 * 
	 * @return a <code>javax.mail.Session</code> value
	 */
	public javax.mail.Session sessionForMessage(ERMessage message) {
		return sessionForContext(message.contextString());
	}

	/**
	 * Returns a new Session object that is appropriate for the given context.
	 * 
	 * If the property <code>er.javamail.sessionConfigViaJNDI</code> is set to <code>true</code> the JNDI is used to lookup the session informations. 
	 * You may change the default JNDI context with the property <code>er.javamail.jndiSessionContext</code> (defaults to <code>java:comp/env/mail</code>).
	 * If you need a different session configuration depending of the message context, you may overwrite the default jndi context with the
	 * property <code>er.javamail.jndiSessionContext._content_of_param_contextString_</code>
	 * 
	 * 
	 * @param contextString
	 *            the message context
	 * @return a new <code>javax.mail.Session</code> value
	 */
	protected javax.mail.Session newSessionForContext(String contextString) {
		javax.mail.Session session = null;
		
		if(log.isDebugEnabled()) log.debug("Create new mail session for context " + contextString);
		boolean jndiLookup = ERXProperties.booleanForKeyWithDefault("er.javamail.sessionConfigViaJNDI", false);
		if (jndiLookup) {
			String jndiContextString = ERXProperties.stringForKeyWithDefault("er.javamail.jndiSessionContext", "java:comp/env/mail");
			if (contextString != null) {
				jndiContextString = ERXProperties.stringForKeyWithDefault("er.javamail.jndiSessionContext." + contextString, jndiContextString);
			}

			try {
				if (log.isDebugEnabled())
					log.debug("try to get javax.mail.Session for JNDI context " + jndiContextString);
				InitialContext ic = new InitialContext();
				session = (javax.mail.Session) ic.lookup(jndiContextString);
			}
			catch (NamingException e) {
				log.error("Failed to initialize JavaMail Session: " + e.getMessage());
			}
		}
		if(session == null) {
			if(jndiLookup) {
				log.info("as the jndiLookup failed, we will try the normal way...");
			}
			if (contextString == null || contextString.length() == 0) {
				session = newSessionForContext(System.getProperties(), contextString);
			}
			else {
				Properties sessionProperties = new Properties();
				sessionProperties.putAll(System.getProperties());
				setupSmtpProperties(sessionProperties, contextString);
				session = newSessionForContext(sessionProperties, contextString);
			}
		}
		return session;
	}

	/**
	 * Returns a newly allocated Session object from the given Properties
	 * 
	 * @param properties
	 *            a <code>Properties</code> value
	 * @return a <code>javax.mail.Session</code> value initialized from the given properties
	 */
	public javax.mail.Session newSessionForContext(Properties properties, String contextString) {
		if (_delegate != null) {
			_delegate.willCreateSessionWithPropertiesForContext(properties, contextString);
		}
		javax.mail.Session session = javax.mail.Session.getInstance(properties);
		if (_delegate != null) {
			_delegate.didCreateSession(session);
		}
		session.setDebug(debugEnabled());
		return session;
	}

	/**
	 * Returns the Session object that is appropriate for the given context.
	 * 
	 * @param contextString
	 *            the message context
	 * @return a <code>javax.mail.Session</code> value
	 */
	protected javax.mail.Session sessionForContext(String contextString) {
		javax.mail.Session session;
		if (contextString == null || contextString.length() == 0) {
			session = defaultSession();
		}
		else {
			session = _sessions.get(contextString);
			if (session == null) {
				session = newSessionForContext(contextString);
				_sessions.put(contextString, session);
			}
		}
		return session;
	}

	/**
	 * email address used when centralizeMails == true <BR>
	 * Needed when debugging application so that mails are always sent to only one destination.
	 */
	protected String _adminEmail;

	/**
	 * <span class="en"> admin email accessor. The admin email is the email address where centralized mail go to.
	 * 
	 * @return a <code>String</code> value </span>
	 * 
	 *         <span class="ja"> centralizeMails == true の場合で使用されるメール・アドレス<br>
	 *         デバッグ中にすべてのメールが一つのターゲットに送信されます。
	 * 
	 * @return メール・アドレス </span>
	 */
	public String adminEmail() {
		return _adminEmail;
	}

	/**
	 * <span class="en"> Sets the admin email to another value. This value is set at initialization from the
	 * <code>er.javamail.adminEmail</code> Property.
	 * 
	 * @param adminEmail
	 *            a <code>String</code> value </span>
	 * 
	 *            <span class="ja"> この値は初期化中で <code>er.javamail.adminEmail</code> プロパティより設定されますが、 このコマンドでオーバライドが可能です。
	 * 
	 * @param adminEmail
	 *            - メール・アドレス </span>
	 */
	public void setAdminEmail(String adminEmail) {
		if (!(isValidEmail(adminEmail) || (adminEmail != null && adminEmail.trim().length() > 0))) {
			throw new IllegalArgumentException("You specified an invalid admin email address '" + adminEmail + "'.");
		}
		_adminEmail = adminEmail;
	}

	/** This property specify wether JavaMail is debug enabled or not. */
	protected boolean _debugEnabled = true;

	/**
	 * <span class="en"> Returns <code>true</code> if JavaMail is debug enabled.
	 * 
	 * @return a <code>boolean</code> value </span>
	 * 
	 *         <span class="ja"> JavaMail がデバッグ中の場合には <code>true</code> が戻ります。
	 * 
	 * @return <code>boolean</code> 値 </span>
	 */
	public boolean debugEnabled() {
		return _debugEnabled;
	}

	/**
	 * <span class="en"> Sets the debug mode of JavaMail.
	 * 
	 * @param debug
	 *            a <code>boolean</code> value sets JavaMail in debug mode </span>
	 * 
	 *            <span class="ja"> JavaMail のデバッグ・モードをセットします。
	 * 
	 * @param debug
	 *            - <code>boolean</code> でデバッグ・モードを On / Off できます </span>
	 */
	public void setDebugEnabled(boolean debug) {
		_debugEnabled = debug;
	}

	/** This property sets the default header for the X-Mailer property */
	protected String _defaultXMailerHeader = null;

	/**
	 * <span class="en"> Gets the default X-Mailer header to use for sending mails. Pulls the value out of the property:
	 * er.javamail.XMailerHeader
	 * 
	 * @return default X-Mailer header </span>
	 * 
	 *         <span class="ja"> 送信時の XMailer ヘッダーのデフォルト値を取得します。 プロパティの er.javamail.XMailerHeader を参照！
	 * 
	 * @return デフォルト X-Mailer ヘッダー </span>
	 */
	public String defaultXMailerHeader() {
		return _defaultXMailerHeader;
	}

	/**
	 * <span class="en"> Sets the default value of the XMailer header used when sending mails.
	 * 
	 * @param header
	 *            a <code>String</code> value </span>
	 * 
	 *            <span class="ja"> 送信時の XMailer ヘッダーのデフォルト値をセットします。
	 * 
	 * @param header
	 *            - <code>String</code> 値 </span>
	 */
	public void setDefaultXMailerHeader(String header) {
		_defaultXMailerHeader = header;
	}

	/** Used to send mail to adminEmail only. Useful for debugging issues */
	protected boolean _centralize = true;

	/**
	 * <span class="en"> Centralize is used to send all the outbound email to a single address which is useful when
	 * debugging.
	 * 
	 * @return a <code>boolean</code> value </span>
	 * 
	 *         <span class="ja"> すべてのメールを er.javamail.adminEmail ユーザに送信します。(デバッグ中に便利)
	 * 
	 * @return <code>boolean</code> 値 </span>
	 */
	public boolean centralize() {
		return _centralize;
	}

	/**
	 * <span class="en"> Sets the value of the <code>er.javamail.centralize</code> Property.
	 * 
	 * @param centralize
	 *            if the boolean value is true, then all the outbound mails will be sent to <code>adminEmail</code>
	 *            email address. </span>
	 * 
	 *            <span class="ja"> <code>er.javamail.centralize</code> プロパティの値をセットします。
	 * 
	 * @param centralize
	 *            - true の場合にはすべてのメールが <code>adminEmail</code> へ送信されます。 </span>
	 */
	public void setCentralize(boolean centralize) {
		_centralize = centralize;
	}

	/**
	 * <span class="en"> Returns the SMTP protocol to use for connections. </span>
	 * 
	 * <span class="ja"> 接続の為の SMTP プロトコールを設定します。 (smtp or smtps)
	 * 
	 * @param contextString
	 *            - SMTP プロトコール名 </span>
	 */
	public String smtpProtocolForContext(String contextString) {
		String contextSuffix = (contextString == null) ? "" : ("." + contextString);
		return ERXProperties.stringForKeyWithDefault("er.javamail.smtpProtocol" + contextSuffix, ERXProperties.stringForKeyWithDefault("er.javamail.smtpProtocol", ERXProperties.stringForKeyWithDefault("mail.smtp.protocol", "smtp")));
	}

	/**
	 * Number of messages that the sender queue can hold at a time; default to 50 messages and can be configured by
	 * <code>er.javamail.senderQueue.size</code> system property.
	 */
	protected int _senderQueueSize = 50;

	public int senderQueueSize() {
		return _senderQueueSize;
	}

	/**
	 * <span class="ja"> 送信キューが一回で保持できるメッセージ数です。 デフォルトでは 50 メッセージで、システム・プロパティの <code>er.javamail.senderQueue.size</code>
	 * で変更可能です。 </span>
	 */
	public void setSenderQueueSize(int value) {
		_senderQueueSize = value;
	}

	/**
	 * <span class="en"> Wait n milliseconds (by default this value is 6000) if the mail sender is overflowed </span>
	 * 
	 * <span class="ja"> メール・キューがオーバフローされている時に待つ時間。 (デフォルトでは 6000) </span>
	 */
	protected int _milliSecondsWaitIfSenderOverflowed = 6000;

	/**
	 * <span class="en"> This method return the time spent waiting if the mail queue if overflowed. During that time,
	 * mails are sent and the queue lowers. When the duration is spent, and the queue is under the overflow limit, the
	 * mails are being sent again.
	 * 
	 * @return an <code>int</code> value </span>
	 * 
	 *         <span class="ja"> メール・キューがオーバフローされている時に待つ時間を設定します。この時間内ではメールが送信され、キューが減ります。
	 *         期間が過ぎるとキューがオーバフロー制限より以下であれば、メールが再度に送信されます。
	 * 
	 * @return <code>int</code> 値 </span>
	 */
	public int milliSecondsWaitIfSenderOverflowed() {
		return _milliSecondsWaitIfSenderOverflowed;
	}

	/**
	 * <span class="en"> Sets the value of the <code>er.javamail.milliSecondsWaitIfSenderOverflowed</code> Property.
	 * 
	 * @param value
	 *            an <code>int</code> value in milli-seconds. </span>
	 * 
	 *            <span class="ja"> <code>er.javamail.milliSecondsWaitIfSenderOverflowed</code> プロパティをセットします。
	 * 
	 * @param value
	 *            - <code>int</code> ミリ秒 </span>
	 */
	public void setMilliSecondsWaitIfSenderOverflowed(int value) {
		_milliSecondsWaitIfSenderOverflowed = value;
	}

	/**
	 * <span class="en"> Validates an enterprise object's email attribute (accessed via key).
	 * 
	 * @param object
	 *            the object to be validated
	 * @param key
	 *            the attribute's name
	 * @param email
	 *            the email value
	 * @return the email if the validation didn't failed </span>
	 * 
	 *         <span class="ja"> エンタプライス・オブジェクトのメール・アトリビュートを検証します。（キーよりのアクセス）
	 * 
	 * @param object
	 *            - 検証するオブジェクト
	 * @param key
	 *            - アトリビュート名
	 * @param email
	 *            - メール値
	 * 
	 * @return 検証が失敗しない場合のメールアドレス </span>
	 */
	public String validateEmail(EOEnterpriseObject object, String key, String email) {
		if (email != null) {
			if (!isValidEmail(email))
				throw ERXValidationFactory.defaultFactory().createException(object, key, email, "malformedEmail");
		}

		return email;
	}

	/**
	 * <span class="en"> Predicate used to validate email well-formness.
	 * 
	 * @return true if the email is valid
	 * @param email
	 *            the email String value to validate
	 * @return a <code>boolean</code> value </span>
	 * 
	 *         <span class="ja"> メールが正しいかどうかを検証します。
	 * 
	 * @param email
	 *            - 検証するメール値
	 * 
	 * @return メールが有効であれば true が戻ります。 </span>
	 */
	public synchronized boolean isValidEmail(String email) {
		if (_pattern == null) {
			String patternString = ERXProperties.stringForKey("er.javamail.emailPattern");
			if (patternString == null || patternString.trim().length() == 0) {
				patternString = EMAIL_VALIDATION_PATTERN;
			}

			try {
				_pattern = Pattern.compile(patternString);
			}
			catch (PatternSyntaxException e) {
				throw new RuntimeException("The compilation of the email pattern '" + patternString + "' failed.", e);
			}
		}
		if (email != null) {
			return _pattern.matcher(email).matches();
		}
		return false;
	}

	// ===========================================================================
	// Deny and Allow email address filtering support
	// メール・フィルター： ホワイト＆ブラック・リスト
	// ---------------------------------------------------------------------------

	/**
	 * <span class="en"> holds the array of allowed email addresses </span>
	 * 
	 * <span class="ja"> ホワイト・リスト・メールアドレス配列を保持 </span>
	 */
	protected NSArray<String> allowEmailAddressPatterns;

	/**
	 * <span class="en"> holds the array of denied email addresses </span>
	 * 
	 * <span class="ja"> ブラック・リスト・メールアドレス配列を保持 </span>
	 */
	protected NSArray<String> denyEmailAddressPatterns;

	/**
	 * <span class="en"> holds the allow list qualifier </span>
	 * 
	 * <span class="ja"> ホワイト・リスト qualifier を保持 </span>
	 */
	protected EOOrQualifier allowQualifier;

	/**
	 * <span class="en"> holds the deny list qualifier </span>
	 * 
	 * <span class="ja"> ブラック・リスト qualifier を保持 </span>
	 */
	protected EOOrQualifier denyQualifier;

	/**
	 * <span class="en"> Determines if a list of allowed email address patterns has been specified
	 * 
	 * @deprecated hasWhiteList() will be removed in future versions. Pleae use {@link ERJavaMail#hasAllowList()} instead
	 * @return if the allow list has any elements in it </span>
	 * 
	 *         <span class="ja"> ホワイト・リストがあるかどうかを戻します。
	 * 
	 * @return ホワイト・リストがある場合には true が戻ります。 </span>
	 */
	@Deprecated
	public boolean hasWhiteList() {
		return hasAllowList();
	}

	/**
	 * <span class="en"> Determines if a list of allowed email address patterns has been specified
	 * 
	 * @return if the allow list has any elements in it </span>
	 * 
	 *         <span class="ja"> ホワイト・リストがあるかどうかを戻します。
	 * 
	 * @return ホワイト・リストがある場合には true が戻ります。 </span>
	 */
	public boolean hasAllowList() {
		return allowEmailAddressPatterns().count() > 0;
	}

	/**
	 * <span class="en"> Determines if a list of email patterns to deny has been specified
	 * 
	 * @deprecated hasBlackList() will be removed from future versions. Please use {@link ERJavaMail#hasDenyList()} instead
	 * @return if the deny list has any elements in it </span>
	 * 
	 *         <span class="ja"> ブラック・リストがあるかどうかを戻します。
	 * 
	 * @return ブラック・リストがある場合には true が戻ります。 </span>
	 */
	@Deprecated
	public boolean hasBlackList() {
		return hasDenyList();
	}

	/**
	 * <span class="en"> Determines if a list of email patterns to deny has been specified
	 * 
	 * @return if the deny list has any elements in it </span>
	 * 
	 *         <span class="ja"> ブラック・リストがあるかどうかを戻します。
	 * 
	 * @return ブラック・リストがある場合には true が戻ります。 </span>
	 */
	public boolean hasDenyList() {
		return denyEmailAddressPatterns().count() > 0;
	}

	/**
	 * <span class="en"> Gets the array of allowed email address patterns.
	 * 
	 * @deprecated This method will be removed in future versions. Please use {@link ERJavaMail#allowEmailAddressPatterns()}
	 * @return array of allowed email address patterns </span>
	 * 
	 *         <span class="ja"> ホワイト・リスト・メールアドレス配列パターンを戻します。
	 * 
	 * @return ホワイト・リスト・メールアドレス配列パターン </span>
	 */
	@SuppressWarnings("unchecked")
	@Deprecated
	public NSArray<String> whiteListEmailAddressPatterns() {
		if (allowEmailAddressPatterns == null) {
			if (ERXProperties.hasKey("er.javamail.WhiteListEmailAddressPatterns") && !ERXProperties.hasKey("er.javamail.AllowEmailAddressPatterns")) {
				log.warn("Please update your properties settings to use \"er.javamail.AllowEmailAddressPatterns\" instead of \"er.javamail.WhiteListEmailAddressPatterns\". whiteListEmailAddressPatterns() and associated properties will be removed from future versions.");
				allowEmailAddressPatterns = ERXProperties.arrayForKeyWithDefault("er.javamail.WhiteListEmailAddressPatterns", NSArray.EmptyArray);
			}
			else {
				allowEmailAddressPatterns = ERXProperties.arrayForKeyWithDefault("er.javamail.AllowEmailAddressPatterns", NSArray.EmptyArray);
			}
		}
		return allowEmailAddressPatterns;
	}

	/**
	 * <span class="en"> Gets the array of allowed email address patterns.
	 * 
	 * @return array of allowed email address patterns </span>
	 * 
	 *         <span class="ja"> TODO: update the japanese documentation ホワイト・リスト・メールアドレス配列パターンを戻します。
	 * 
	 * @return ホワイト・リスト・メールアドレス配列パターン </span>
	 */
	@SuppressWarnings("unchecked")
	public NSArray<String> allowEmailAddressPatterns() {
		if (allowEmailAddressPatterns == null) {
			allowEmailAddressPatterns = ERXProperties.arrayForKeyWithDefault("er.javamail.AllowEmailAddressPatterns", NSArray.EmptyArray);
		}
		return allowEmailAddressPatterns;
	}

	/**
	 * <span class="en"> Gets the array of denied email address patterns.
	 * 
	 * @deprecated This will be removed from future versions. Please use {@link ERJavaMail#denyEmailAddressPatterns}
	 * @return array of denied email address patterns </span>
	 * 
	 *         <span class="ja"> TODO: update Japanese documentation ブラック・リスト・メールアドレス配列パターンを戻します。
	 * 
	 * @return ブラック・リスト・メールアドレス配列パターン </span>
	 */
	public NSArray<String> blackListEmailAddressPatterns() {
		if (denyEmailAddressPatterns == null) {
			if (ERXProperties.hasKey("er.javamail.BlackListEmailAddressPatterns") && !ERXProperties.hasKey("er.javamail.DenyEmailAddressPatterns")) {
				log.warn("Please update your properties settings to use \"er.javamail.DenyEmailAddressPatterns\" instead of \"er.javamail.BlackListEmailAddressPatterns\". blackListEmailAddressPatterns() and associated properties will be removed from future versions.");
				denyEmailAddressPatterns = ERXProperties.arrayForKeyWithDefault("er.javamail.BlackListEmailAddressPatterns", NSArray.emptyArray());
			}
			else {
				denyEmailAddressPatterns = ERXProperties.arrayForKeyWithDefault("er.javamail.DenyEmailAddressPatterns", NSArray.emptyArray());
			}
		}
		return denyEmailAddressPatterns;
	}

	/**
	 * <span class="en"> Gets the array of denied email address patterns.
	 * 
	 * @return array of denied email address patterns </span>
	 * 
	 *         <span class="ja"> TODO: update Japanese documentation ブラック・リスト・メールアドレス配列パターンを戻します。
	 * @return ブラック・リスト・メールアドレス配列パターン </span>
	 */
	public NSArray<String> denyEmailAddressPatterns() {
		if (denyEmailAddressPatterns == null) {
			denyEmailAddressPatterns = ERXProperties.arrayForKeyWithDefault("er.javamail.DenyEmailAddressPatterns", NSArray.emptyArray());
		}
		return denyEmailAddressPatterns;
	}

	/**
	 * <span class="en"> {@link EOOrQualifier} qualifier to match any of the patterns in the allow list.
	 * 
	 * @deprecated whiteListQualifier() will be removed in future versions. Please use {@link ERJavaMail#allowQualifier()}
	 *             instead.
	 * @return Or qualifier for the allow list </span> <span class="ja"> ホワイト・リスト内でマッチするパタンのホワイト・リスト Or qualifier
	 * 
	 * @return ホワイト・リスト Or qualifier </span>
	 */
	@Deprecated
	public EOOrQualifier whiteListQualifier() {
		return allowQualifier();
	}

	/**
	 * <span class="en"> {@link EOOrQualifier} qualifier to match any of the patterns in the allow list.
	 * 
	 * @return {@link EOOrQualifier} for the allow list </span> <span class="ja"> ホワイト・リスト内でマッチするパタンのホワイト・リスト Or qualifier
	 * 
	 * @return ホワイト・リスト Or qualifier </span>
	 */
	public EOOrQualifier allowQualifier() {
		if (allowQualifier == null) {
			allowQualifier = qualifierArrayForEmailPatterns(allowEmailAddressPatterns());
		}
		return allowQualifier;
	}

	/**
	 * <span class="en"> Gets the Or qualifier to match any of the patterns in the deny list.
	 * 
	 * @deprecated blackListQualifier() will be removed in future versions. Please use {@denyQualifier()}
	 * @return or qualifier </span>
	 * 
	 *         <span class="ja"> ブラック・リスト内でマッチするパタンのブラック・リスト Or qualifier
	 * 
	 * @return ブラック・リスト Or qualifier </span>
	 */
	@Deprecated
	public EOOrQualifier blackListQualifier() {
		return denyQualifier();
	}

	/**
	 * <span class="en"> Gets the {@link EOOrQualifier} qualifier to match any of the patterns in the deny list.
	 * 
	 * @return or qualifier </span>
	 * 
	 *         <span class="ja"> ブラック・リスト内でマッチするパタンのブラック・リスト Or qualifier
	 * 
	 * @return ブラック・リスト Or qualifier </span>
	 */
	public EOOrQualifier denyQualifier() {
		if (denyQualifier == null) {
			denyQualifier = qualifierArrayForEmailPatterns(denyEmailAddressPatterns());
		}
		return denyQualifier;
	}

	/**
	 * <span class="en"> Constructs an Or qualifier for filtering an array of strings that might have the * wildcard
	 * character. Will be nice when we have regex in Java 1.4.
	 * 
	 * @param emailPatterns
	 *            array of email patterns
	 * @return or qualifier to match any of the given patterns </span>
	 * 
	 *         <span class="ja"> ワイルドカード文字 * を持つ配列をフィルターする Or qualifier を作成します。
	 * 
	 * @param emailPatterns
	 *            - メール・パタンの配列
	 * 
	 * @return 指定パタンのマッチに使用する or qualifier </span>
	 */
	protected EOOrQualifier qualifierArrayForEmailPatterns(NSArray<String> emailPatterns) {
		NSMutableArray<EOQualifier> patternQualifiers = new NSMutableArray<>();
		for (String pattern : emailPatterns) {
			patternQualifiers.addObject(EOQualifier.qualifierWithQualifierFormat("toString caseInsensitiveLike '" + pattern + "'", null));
		}
		return new EOOrQualifier(patternQualifiers);
	}

	/**
	 * <span class="en"> Filters an array of email addresses by the deny and allow lists.
	 * 
	 * @param emailAddresses
	 *            array of email addresses to be filtered
	 * @return array of filtered email addresses </span>
	 * 
	 *         <span class="ja"> メールアドレス配列をホワイト＆ブラック・リストでフィルターします。
	 * 
	 * @param emailAddresses
	 *            - フィルターするメール・アドレス配列
	 * 
	 * @return フィルター済みのメールアドレス配列 </span>
	 */
	public NSArray<String> filterEmailAddresses(NSArray<String> emailAddresses) {
		NSMutableArray<String> filteredAddresses = null;
		if ((emailAddresses != null) && (emailAddresses.count() > 0) && (hasAllowList() || hasDenyList())) {
			filteredAddresses = new NSMutableArray<>(emailAddresses);

			if (log.isDebugEnabled()) {
				log.debug("Filtering email addresses: " + filteredAddresses);
			}

			if (hasAllowList()) {
				EOQualifier.filterArrayWithQualifier(filteredAddresses, allowQualifier());
				if (log.isDebugEnabled()) {
					log.debug("Allow qualifier: " + allowQualifier() + " after filtering: " + filteredAddresses);
				}
			}

			if (hasDenyList()) {
				NSArray<String> filteredOutAddresses = EOQualifier.filteredArrayWithQualifier(filteredAddresses, denyQualifier());
				if (filteredOutAddresses.count() > 0)
					filteredAddresses.removeObjectsInArray(filteredOutAddresses);
				if (log.isDebugEnabled()) {
					log.debug("Deny qualifier: " + denyQualifier() + " filtering: " + filteredAddresses);
				}
			}
		}

		return (filteredAddresses != null) ? filteredAddresses.immutableClone() : emailAddresses;
	}

	public static interface Delegate {
		public void willCreateSessionWithPropertiesForContext(Properties properties, String contextString);

		public void didCreateSession(javax.mail.Session session);
	}
}
