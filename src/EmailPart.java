import java.util.Vector;

public class EmailPart {
	int key;
	int parentKey;
	int emailKey;
	int referencedEmailKey = 0;
	String contentType;
	String fileName;
	String decodedContent;
	byte[] content;

	Vector<EmailPart> children = new Vector<EmailPart>();

	public void addChild(EmailPart child) {
		child.emailKey = emailKey;
		child.parentKey = key;
		children.add(child);
	}
}
