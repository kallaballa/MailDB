import java.util.*;
import java.util.concurrent.Semaphore;
import java.io.*;

import javax.mail.*;
import javax.mail.internet.*;

import java.text.SimpleDateFormat;

public class MailImport {
	private MailDB db;
	private Store store;
	private Session session;

	public MailImport(File configFile, String[] mboxNames)
			throws Exception {
		try {
			System.out.print("Loading DB configuration... ");
			Properties importProps = loadConfig(configFile);
			System.out.println("Done");

			System.out.print("Connecting to DB... ");
			this.db = new MailDB(importProps);
			this.db.connect();
			System.out.println("Done");

			System.out.print("Connecting to Mailbox... ");
			this.store = openMbox(importProps);
			System.out.println("Done");
			
			final Semaphore sema = new Semaphore(Integer.parseInt(importProps.getProperty("threads")));
			for (int i = 0; i < mboxNames.length; i++) {
				final String name = mboxNames[i];
				try {
					System.out.println(name + " open... ");
					Folder f = openFolder(store, name);

					System.out.println(name + " fetch messages... ");
					final Message[] msgs = fetch(f);
					
					sema.acquire();
					
					new Thread(){ public void run(){
						try {
							System.out.println(name + " import " + msgs.length
									+ " messages... ");
							importMessages(name, msgs);
							System.out.println(name + " done ");
						} catch (Throwable t) {
							t.printStackTrace();
						} finally {
							sema.release();
						}
					}}.start();
				} catch (Throwable t) {
					System.err.println("### FAILED: " + name);
					t.printStackTrace();
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.flush();
			System.exit(2);
		} finally {
			/*try {
				db.close();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			try {
				store.close();
			} catch (Exception e1) {
				e1.printStackTrace();
			}*/
		}
	}

	public void importMessages(String name, Message[] msgs) throws MailImportException {
		for (int i = 0; i < msgs.length; i++) {
			EmailMessage email = null;
			try {
				if ((email = importEnvelope(msgs[i], i, false)) == null)
					throw new MailImportException("No Email returned", null,
							null);

				importPart(email, msgs[i]);
				db.commit();
				msgs[i] = null;
			} catch (MailImportException mbex) {
				mbex.handleException();
			} catch (Throwable t) {
				System.err.println("Failed: " + name);
				t.printStackTrace();
			}

		}
	}

	public Properties loadConfig(File configFile) throws IOException {
		FileReader r = new FileReader(configFile);
		Properties dbProps = new Properties();
		dbProps.load(r);
		r.close();
		return dbProps;
	}

	public Store openMbox(Properties importProps) throws MessagingException {
		
		Properties props = System.getProperties();
		this.session = Session.getInstance(props, null);
		Store store = session.getStore("mbox");
		store.connect();
		System.out.println("Store connected");

		return store;
	}

	public Folder openFolder(Store store, String name)
			throws MessagingException {
	    //Folder folder = store.getFolder(name);
	    Folder folder = store.getDefaultFolder();
	    
		folder.open(Folder.READ_ONLY);

		int totalMessages = folder.getMessageCount();

		if (totalMessages == 0) {
			folder.close(false);
			store.close();
			throw new RuntimeException("Empy mailbox folder");
		}
		return folder;
	}

	public Message[] fetch(Folder folder) throws MessagingException {
		Message[] msgs = folder.getMessages();
		FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		fp.add(FetchProfile.Item.FLAGS);
		fp.add("X-Mailer");
		folder.fetch(msgs, fp);

		return msgs;
	}

	private Address[] getInternetAddresses(MimeMessage m, String name)
			throws MessagingException {
		String value = m.getHeader(name, ",");

		// ### MICROSOFT OUTLOOK WORKAROUND ### //
		if (value != null && value.indexOf("<<") > -1) {
			value = (value = value.replace("<<", "<")).replace(">>", ">");
		}

		String s = session.getProperty("mail.mime.address.strict");
		boolean strict = (s == null) || Boolean.valueOf(s).booleanValue();
		return (value != null) ? InternetAddress.parseHeader(value, strict)
				: null;
	}

	public EmailMessage importEnvelope(Message m, int nr, boolean referenced)
			throws MailImportException {
		InternetAddress[] a;
		EmailMessage email = new EmailMessage();
		email.nr = nr;
		email.referenced = referenced;

		try {
			if ((a = (InternetAddress[]) getInternetAddresses((MimeMessage) m,
					"From")) != null)
				email.from = db.insertSubscriber(a);

			if ((a = (InternetAddress[]) getInternetAddresses((MimeMessage) m,
					"Reply-to")) != null)
				email.replyto = db.insertSubscriber(a);

			if ((a = (InternetAddress[]) getInternetAddresses((MimeMessage) m,
					"To")) != null)
				email.to = db.insertSubscriber(a);

			if ((a = (InternetAddress[]) getInternetAddresses((MimeMessage) m,
					"Cc")) != null)
				email.cc = db.insertSubscriber(a);

			email.subject = m.getSubject();
			Date senddate = m.getSentDate();

			if (senddate != null) {
				email.senddate = new java.sql.Date(senddate.getTime());
			}

			String[] header;
			if ((header = m.getHeader("X-Mailer")) != null)
				email.xmailer = header[0];
			if ((header = m.getHeader("Message-ID")) != null)
				email.messageid = header[0];
			if ((header = m.getHeader("User-Agent")) != null)
				email.useragent = header[0];

			if (db.insertEnvelope(email))
				return email;
			else
				return null;
		} catch (Exception e) {
			throw new MailImportException("Message import failed", email, e);
		}
	}

	public EmailPart importPart(EmailMessage email, Part p)
			throws MailImportException {
		try {
			EmailPart ep = buildPartTree(email, p);
			ep.emailKey = email.key;
			ep.parentKey = email.key;

			if (db.insertPart(ep)) {
				db.insertChildParts(ep);
				return ep;
			} else
				return null;
		} catch (MailImportException mbex) {
			mbex.handleException();
			return null;
		} catch (Exception e) {
			throw new MailImportException("Part import failed", email, e);
		}
	}

	public EmailPart buildPartTree(EmailMessage parent, Part p)
			throws MailImportException {
		EmailPart ep = new EmailPart();
		EmailMessage referencedEmail = null;

		try {
			if (p instanceof Message) {
				if ((referencedEmail = importEnvelope((Message) p, -1, true)) != null)
					ep.referencedEmailKey = referencedEmail.key;
			}

			ep.contentType = p.getContentType();
			Object content = null;
			try {
				ep.fileName = p.getFileName();

				try {
					// ### ANOTHER OUTLOOK WORKAROUND
					if (ep.contentType.contains("UTF-7")) {
						ep.contentType.replace("UTF-7", "UTF-8");
						p.setHeader("Content-Type", ep.contentType);

						content = p.getDataHandler().getInputStream();
					}

					if (content == null)
						content = p.getContent();
				} catch (Exception e) {
					if (MailDB.debug)
						e.printStackTrace();
				}

				if (content != null) {
					if (p.isMimeType("text/plain")) {
						ep.decodedContent = content.toString();
						ep.content = ep.decodedContent.getBytes();
					} else if (p.isMimeType("multipart/*")
							&& (content instanceof Multipart || content instanceof MimeMultipart)) {
						Multipart mp = (Multipart) content;
						int count = mp.getCount();

						for (int i = 0; i < count; i++)
							ep
									.addChild(buildPartTree(parent, mp
											.getBodyPart(i)));
					} else if (p.isMimeType("message/rfc822")) {
						ep.addChild(buildPartTree(parent, (Part) content));
					}
				}
			} catch (javax.mail.internet.ParseException ex) {
			}

			if (content != null && ep.content == null) {
				if (content instanceof InputStream) {
					ep.content = readByteArray((InputStream) content);
				} else if (content instanceof String) {
					ep.decodedContent = content.toString();
					ep.content = ep.decodedContent.getBytes();
				}
			}
		} catch (Exception e) {
			throw new MailImportException("Content parsing failed: "
					+ e.getMessage(), parent, e);
		}
		return ep;
	}

	public static byte[] readByteArray(InputStream in) throws IOException {
		try {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			int c;
			while ((c = in.read()) != -1)
				buffer.write(c);

			return buffer.toByteArray();
		} catch (Exception e) {
			if (MailDB.debug)
				e.printStackTrace();
			return null;
		}
	}

	public static void main(String argv[]) {
		try {
			if (argv.length != 2) {
				System.err
						.println("Usage: MailImport <config file> <mailboxlist file>");
				System.exit(1);
			}

			File configFile = new File(argv[0]);
			File mailBoxes = new File(argv[1]);
			Vector<String> vecNames = new Vector<String>();
			String line;
			BufferedReader reader = new BufferedReader(new FileReader(mailBoxes));
			while((line = reader.readLine()) != null)
				vecNames.add(line);

			reader.close();

			new MailImport(configFile, vecNames.toArray(new String[0]));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		System.exit(0);
	}
}
