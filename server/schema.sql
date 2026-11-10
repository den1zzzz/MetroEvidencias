IF OBJECT_ID('dbo.AppUsers') IS NULL
CREATE TABLE dbo.AppUsers(
 Username nvarchar(50) NOT NULL PRIMARY KEY,
 PasswordHash varbinary(32) NOT NULL,
 Salt varbinary(16) NOT NULL,
 Role varchar(20) NOT NULL CHECK(Role IN ('supervisor','worker')),
 CreatedAt datetimeoffset NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
IF OBJECT_ID('dbo.Sessions') IS NULL
CREATE TABLE dbo.Sessions(
 TokenHash binary(32) NOT NULL PRIMARY KEY,
 Username nvarchar(50) NOT NULL REFERENCES dbo.AppUsers(Username),
 ExpiresAt datetimeoffset NOT NULL
);
IF OBJECT_ID('dbo.Evidence') IS NULL
CREATE TABLE dbo.Evidence(
 Id uniqueidentifier NOT NULL PRIMARY KEY,
 RecordId uniqueidentifier NOT NULL,
 Author nvarchar(50) NOT NULL REFERENCES dbo.AppUsers(Username),
 WorkType nvarchar(30) NOT NULL,
 Location nvarchar(200) NOT NULL,
 Tag nvarchar(80) NOT NULL,
 Description nvarchar(2000) NOT NULL,
 Gps nvarchar(500) NOT NULL,
 CapturedAt datetimeoffset NOT NULL,
 ReceivedAt datetimeoffset NOT NULL DEFAULT SYSDATETIMEOFFSET(),
 Metadata nvarchar(max) NOT NULL CHECK(ISJSON(Metadata)=1),
 ContentHash binary(32) NOT NULL,
 Image varbinary(max) NOT NULL,
 Review nvarchar(30) NOT NULL DEFAULT N'Pendiente de revisión'
   CHECK(Review IN (N'Pendiente de revisión',N'Revisada',N'Observada')),
 Reviewer nvarchar(50) NULL REFERENCES dbo.AppUsers(Username),
 ReviewedAt datetimeoffset NULL
);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_Evidence_CapturedAt' AND object_id=OBJECT_ID('dbo.Evidence'))
CREATE INDEX IX_Evidence_CapturedAt ON dbo.Evidence(CapturedAt DESC) INCLUDE(WorkType, Location, Tag, Author, Review);
-- Additive migration: existing evidence remains unclassified by shift until explicitly known.
IF COL_LENGTH('dbo.Evidence','Shift') IS NULL
 ALTER TABLE dbo.Evidence ADD Shift nvarchar(30) NOT NULL CONSTRAINT DF_Evidence_Shift DEFAULT N'Sin definir' WITH VALUES;
IF COL_LENGTH('dbo.Evidence','WorkDate') IS NULL
 ALTER TABLE dbo.Evidence ADD WorkDate date NULL;
IF COL_LENGTH('dbo.Evidence','Environment') IS NULL
 ALTER TABLE dbo.Evidence ADD Environment nvarchar(120) NOT NULL CONSTRAINT DF_Evidence_Environment DEFAULT N'General' WITH VALUES;
IF COL_LENGTH('dbo.Evidence','SiteCode') IS NULL
 ALTER TABLE dbo.Evidence ADD SiteCode nvarchar(150) NOT NULL CONSTRAINT DF_Evidence_SiteCode DEFAULT N'' WITH VALUES;
IF COL_LENGTH('dbo.Evidence','Activity') IS NULL
 ALTER TABLE dbo.Evidence ADD Activity nvarchar(80) NOT NULL CONSTRAINT DF_Evidence_Activity DEFAULT N'' WITH VALUES;
IF COL_LENGTH('dbo.Evidence','OriginalImage') IS NULL
 ALTER TABLE dbo.Evidence ADD OriginalImage varbinary(max) NULL;
IF COL_LENGTH('dbo.Evidence','OriginalHash') IS NULL
 ALTER TABLE dbo.Evidence ADD OriginalHash binary(32) NULL;
EXEC(N'UPDATE dbo.Evidence SET WorkDate=CONVERT(date,SWITCHOFFSET(CapturedAt,''-05:00'')) WHERE WorkDate IS NULL');
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.Evidence') AND name='Tag' AND max_length<1000)
BEGIN
 IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.Evidence') AND name='IX_Evidence_CapturedAt') DROP INDEX IX_Evidence_CapturedAt ON dbo.Evidence;
 ALTER TABLE dbo.Evidence ALTER COLUMN Tag nvarchar(500) NOT NULL;
 CREATE INDEX IX_Evidence_CapturedAt ON dbo.Evidence(CapturedAt DESC) INCLUDE(WorkType,Location,Tag,Author,Review);
END;
