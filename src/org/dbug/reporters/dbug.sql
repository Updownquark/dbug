--The schema for the DBug persistence utility, written by org.dbug.reporters.DBReporter.
--Engineered against the H2 database grammar
DROP SCHEMA IF EXISTS dbug;

CREATE SCHEMA dbug;

CREATE TABLE dbug.Process (
	id BIGINT NOT NULL AUTO_INCREMENT,
	host VARCHAR(64) NOT NULL,
	process_id INTEGER NOT NULL,
	start_time TIMESTAMP NOT NULL,
	name VARCHAR(255) NULL, -- May not be populated by the process itself, but perhaps renamed in external UI

	PRIMARY KEY(id)
);

CREATE INDEX dbug.Process_By_Host ON dbug.Process(host);
CREATE INDEX dbug.Process_By_Time ON dbug.Process(start_time);

CREATE TABLE dbug.Dbug_Schema (
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	name VARCHAR(255) NOT NULL,

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	UNIQUE(process, name)
);

CREATE INDEX dbug.Dbug_Schema_By_Name ON dbug.Dbug_Schema(name);

--Statically-declared anchor type information

CREATE TABLE dbug.Anchor_Type (
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	dbug_schema BIGINT NOT NULL,
	class_name VARCHAR(512) NOT NULL,

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, dbug_schema) REFERENCES dbug.Dbug_Schema(process, id),
	UNIQUE(process, dbug_schema, class_name)
);

CREATE INDEX dbug.Anchor_Type_By_Class_Name ON dbug.Anchor_Type(class_name);

CREATE TABLE dbug.Anchor_Field(
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	anchor_type BIGINT NOT NULL,
	name VARCHAR(255) NOT NULL,
	field_type INT NOT NULL, --0 for static, 1 for dynamic

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, anchor_type) REFERENCES dbug.Anchor_Type(process, id),
	UNIQUE(process, anchor_type, name)
);

CREATE TABLE dbug.Event_Type(
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	anchor_type BIGINT NOT NULL,
	name VARCHAR(255) NOT NULL,

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, anchor_type) REFERENCES dbug.Anchor_Type(process, id),
	UNIQUE(process, anchor_type, name)
);

CREATE TABLE dbug.Event_Field(
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	event_type BIGINT NOT NULL,
	name VARCHAR(255) NOT NULL,

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, event_type) REFERENCES dbug.Event_Type(process, id),
	UNIQUE(process, event_type, name)
);

--Declared config information

CREATE TABLE dbug.Config(
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	anchor_type BIGINT NOT NULL,
	config_id VARCHAR(255) NOT NULL,
	condition BIGINT NULL, --Nullable because the Config_Value.config field is not nullable.  This must be populated shortly after insertion.

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, anchor_type) REFERENCES dbug.Anchor_Type(process, id)
);

CREATE TABLE dbug.Config_Value(
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	config BIGINT NOT NULL,
	name VARCHAR(255) NULL, --Null for conditions only

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, config) REFERENCES dbug.Config(process, id),
	UNIQUE (process, config, name)
);

CREATE TABLE dbug.Config_Event(
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	config BIGINT NOT NULL,
	event_type BIGINT NOT NULL,

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, config) REFERENCES dbug.Config(process, id),
	FOREIGN KEY(process, event_type) REFERENCES dbug.Event_Type(process, id)
);

CREATE TABLE dbug.Config_Event_Value(
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	config_event BIGINT NOT NULL,
	name VARCHAR(255) NULL, --Null for event conditions only

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, config_event) REFERENCES dbug.Config_Event(process, id),
	UNIQUE(process, config_event, name)
);

--Anchor instance information

CREATE TABLE dbug.Anchor(
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	anchor_type BIGINT not null,

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, anchor_type) REFERENCES dbug.Anchor_Type(process, id)
);

CREATE TABLE dbug.Anchor_Config_Instance(
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	config BIGINT NOT NULL,
	anchor BIGINT NOT NULL,

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, config) REFERENCES dbug.Config(process, id),
	FOREIGN KEY(process, anchor) REFERENCES dbug.Anchor(process, id),
	UNIQUE (process, config, anchor)
);

--Event instance information

CREATE TABLE dbug.Event_Instance(
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	event_type BIGINT NOT NULL,
	anchor BIGINT NOT NULL,
	thread_id BIGINT NOT NULL,
	start_time TIMESTAMP NOT NULL,
	end_time TIMESTAMP NULL, --Null if the event has not finished

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, event_type) REFERENCES dbug.Event_Type(process, id),
	FOREIGN KEY(process, anchor) REFERENCES dbug.Anchor(process, id)
);

CREATE TABLE dbug.Event_Config_Instance(
	process BIGINT NOT NULL,
	id BIGINT NOT NULL,
	anchor BIGINT NOT NULL,
	event BIGINT NOT NULL,

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, anchor) REFERENCES dbug.Anchor_Config_Instance(process, id),
	FOREIGN KEY(process, event) REFERENCES dbug.Event_Instance(process, id),
	UNIQUE (process, anchor, event)
);

CREATE TABLE dbug.Event_Value( --Either an event field or a event config-value value
	process BIGINT NOT NULL,
	field BIGINT NULL, --Null if this represents a config variable value
	event_value BIGINT NULL, --Null if this represents an event field value
	event BIGINT NOT NULL, --Actually references Event_Config_Instance
	value_str VARCHAR(512), --The toString() representation of the event field/variable value

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, field) REFERENCES dbug.Event_Field(process, id),
	FOREIGN KEY(process, event_value) REFERENCES dbug.Config_Event_Value(process, id),
	FOREIGN KEY(process, event) REFERENCES dbug.Event_Config_Instance(process, id)
);

--Anchor state information

CREATE TABLE dbug.Anchor_Value( --Either an anchor field or a config-value value
	process BIGINT NOT NULL,
	field BIGINT NULL, --Null if this represents a config variable value
	config_value BIGINT NULL, --Null if this represents an anchor field value
	event BIGINT NOT NULL, --Actually references Event_Config_Instance. The event where the value was reported with an initial or new value.
	value_str VARCHAR(512), --The toString() representation of the field/variable value

	PRIMARY KEY(process, id),
	FOREIGN KEY(process) REFERENCES dbug.Process(id),
	FOREIGN KEY(process, field) REFERENCES dbug.Anchor_Field(process, id),
	FOREIGN KEY(process, config_value) REFERENCES dbug.Config_Value(process, id),
	FOREIGN KEY(process, event) REFERENCES dbug.Event_Config_Instance(process, id)
);
