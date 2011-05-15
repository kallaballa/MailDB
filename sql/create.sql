use mail;

CREATE TABLE Envelope_Part (
  idEnvelope INT NOT NULL, 
  idPart INT NOT NULL, 
  idParent INT,
  PRIMARY KEY(idEnvelope,idPart)
);

CREATE TABLE Part (
  idPart INT NOT NULL AUTO_INCREMENT,  
  filename TEXT, 
  content LONGBLOB, 
  contentType TEXT, 
  contentLength int, 
  decodedContent LONGTEXT,
  idReferencedEnvelope int,
  PRIMARY KEY(idPart)
);

CREATE TABLE Envelope (
  idEnvelope INT NOT NULL AUTO_INCREMENT,
  messageID VARCHAR(256),
  subject TEXT, 
  sendDate DATE, 
  xmailer TEXT, 
  useragent TEXT,
  UNIQUE KEY(messageID),
  PRIMARY KEY(idEnvelope)
);

CREATE TABLE Subscriber (
  idSubscriber INT NOT NULL AUTO_INCREMENT,
  address VARCHAR(128) NOT NULL,
  name VARCHAR(128),
  UNIQUE KEY(address,name),
  PRIMARY KEY(idSubscriber)
);

CREATE TABLE Envelope_Subscriber (
  idEnvelope INT NOT NULL,
  idSubscriber INT NOT NULL,
  type ENUM('From','To','CC','ReplyTo'),
  PRIMARY KEY(idEnvelope,idSubscriber)
);