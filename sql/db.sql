CREATE TABLE Envelope_Part (
  idEnvelope INT NOT NULL, 
  idPart INT NOT NULL, 
  idParent INT
);

CREATE TABLE Part (
  idPart INT NOT NULL AUTO_INCREMENT,  
  filename TEXT, 
  content blob, 
  contentType TEXT, 
  contentLength int, 
  decodedContent TEXT, 
  idReferencedEnvelope messageID int
  PRIMARY KEY(idPart)
);

CREATE TABLE Envelope (
  idEnvelope INT NOT NULL AUTO_INCREMENT,
  messageID INT NOT NULL,
  subject TEXT, 
  sendDate DATE, 
  xmailer TEXT, 
  useragent TEXT,
  PRIMARY KEY(idEnvelope, messageID)
);


CREATE TABLE Subscriber (
  idSubscriber INT NOT NULL AUTO_INCREMENT,
  Name TEXT,
  PRIMARY KEY(idSubscribe)
);

CREATE TABLE Envelope_Subscriber (
  idEnvelope INT NOT NULL AUTO_INCREMENT,
  idSubscriber INT NOT NULL AUTO_INCREMENT,
  type ENUM('From','To','CC','ReplyTo'),
  PRIMARY KEY(idEnvelope,idSubscribe)
);