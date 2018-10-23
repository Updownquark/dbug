package org.dbug.reporters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.dbug.DBug;
import org.dbug.DBugAnchor;
import org.dbug.DBugAnchorType;
import org.dbug.DBugEvent;
import org.dbug.DBugEventType;
import org.dbug.config.DBugConfig;
import org.dbug.config.DBugConfig.DBugEventConfig;
import org.dbug.config.DBugConfigEvent;
import org.dbug.config.DBugConfiguredAnchor;
import org.dbug.config.DBugEventReporter;
import org.qommons.BiTuple;
import org.qommons.Transaction;
import org.qommons.collect.ParameterSet.ParameterMap;
import org.qommons.config.QommonsConfig;

/** Persists all anchor and event data received into a relation DB, in the dbug schema defined by dbug.sql */
public class DBReporter implements DBugEventReporter<DBReporter.DBCompiledAnchorConfig, DBReporter.DBCompiledEventConfig, //
	DBReporter.DBCompiledAnchor, DBReporter.DBCompiledConfiguredAnchor, DBReporter.DBCompiledEvent> {
	private Connection theConnection;

	long theProcessId;
	final Map<String, Long> theSchemaIds;
	final Map<BiTuple<String, String>, DBCompiledAnchorType> theAnchorTypes;
	final ThreadLocal<Long> theThreadIds;

	final AtomicLong theSchemaIdGen;
	PreparedStatement theSchemaInsert;
	final AtomicLong theAnchorTypeIdGen;
	PreparedStatement theAnchorTypeInsert;
	final AtomicLong theAnchorFieldIdGen;
	PreparedStatement theAnchorFieldInsert;
	final AtomicLong theEventTypeIdGen;
	PreparedStatement theEventTypeInsert;
	final AtomicLong theEventFieldIdGen;
	PreparedStatement theEventFieldInsert;
	final AtomicLong theConfigIdGen;
	PreparedStatement theConfigInsert;
	PreparedStatement theConfigConditionUpdate;
	final AtomicLong theConfigValueIdGen;
	PreparedStatement theConfigValueInsert;
	final AtomicLong theConfigEventIdGen;
	PreparedStatement theConfigEventInsert;
	final AtomicLong theConfigEventValueIdGen;
	PreparedStatement theConfigEventValueInsert;
	final AtomicLong theAnchorIdGen;
	PreparedStatement theAnchorInsert;
	PreparedStatement theAnchorValueInsert;
	final AtomicLong theAnchorConfigIdGen;
	PreparedStatement theAnchorConfigInsert;
	PreparedStatement theEventInsert;
	PreparedStatement theEventEndUpdate;
	PreparedStatement theEventValueInsert;
	final AtomicLong theEventConfigIdGen;
	PreparedStatement theEventConfigInsert;

	/** Creates the reporter */
	public DBReporter() {
		theSchemaIds = new ConcurrentHashMap<>();
		theAnchorTypes = new ConcurrentHashMap<>();
		theThreadIds = ThreadLocal.withInitial(() -> Thread.currentThread().getId());

		theSchemaIdGen = new AtomicLong();
		theAnchorTypeIdGen = new AtomicLong();
		theAnchorFieldIdGen = new AtomicLong();
		theEventTypeIdGen = new AtomicLong();
		theEventFieldIdGen = new AtomicLong();
		theConfigIdGen = new AtomicLong();
		theConfigValueIdGen = new AtomicLong();
		theConfigEventIdGen = new AtomicLong();
		theConfigEventValueIdGen = new AtomicLong();
		theAnchorIdGen = new AtomicLong();
		theAnchorConfigIdGen = new AtomicLong();
		theEventConfigIdGen = new AtomicLong();
	}

	@Override
	public void configure(QommonsConfig config) {
		String url = config.get("jdbc-url");
		if (url == null) {
			System.err.println(getClass().getSimpleName() + ": jdbc-url attribute expected. Cannot configure DB reporter.");
			return;
		}
		String username = config.get("user");
		String password = config.get("password");
		try {
			theConnection = DriverManager.getConnection(url, username, password);
		} catch (SQLException e) {
			System.err.println(
				getClass().getSimpleName() + ": Could not connect to " + url + " as " + username + ". Cannot configure DB reporter.");
			e.printStackTrace();
			return;
		}
		// Prepare the statements
		if (!prepareStatements()) {
			// Couldn't prepare the statements for some reason that has already been printed to output.
			// Close the connection
			try {
				theConnection.close();
			} catch (SQLException e3) {
				System.err.println(getClass().getSimpleName() + "Error closing connection");
				e3.printStackTrace();
			}
		}
		// Insert the process into the DB and get the process ID which is a key for everything else
		if (!insertProcess()) {
			// Couldn't insert the process for some reason that has already been printed to output.
			// Close the connection
			try {
				theConnection.close();
			} catch (SQLException e3) {
				System.err.println(getClass().getSimpleName() + "Error closing connection");
				e3.printStackTrace();
			}
		}
	}

	@Override
	public DBCompiledAnchorConfig compileForAnchorConfig(DBugConfig<?> anchor) {
		if (theConnection == null)
			return null;
		DBCompiledAnchorConfig compiledAnchor = new DBCompiledAnchorConfig(this, anchor);
		DBug.queueAction(compiledAnchor::persist);
		return compiledAnchor;
	}

	@Override
	public DBCompiledEventConfig compileForEventConfig(DBCompiledAnchorConfig compiledAnchorType, DBugEventConfig<?> event) {
		if (theConnection == null)
			return null;
		for (DBCompiledEventConfig evtConfig : compiledAnchorType.theEventConfigs.get(event.eventType.getEventIndex())) {
			if (evtConfig.theEventConfig == event)
				return evtConfig;
		}
		throw new IllegalStateException(
			"Unrecognized event config for " + event.eventType.getAnchorType() + "." + event.eventType.getEventName());
	}

	@Override
	public DBCompiledAnchor compileForAnchor(DBugAnchor<?> anchor) {
		if (theConnection == null)
			return null;
		DBCompiledAnchor compiledAnchor = new DBCompiledAnchor(this, anchor);
		DBug.queueAction(compiledAnchor::persist);
		return compiledAnchor;
	}

	@Override
	public DBCompiledConfiguredAnchor compileForConfiguredAnchor(DBCompiledAnchor compiledAnchor, DBCompiledAnchorConfig compiledConfig,
		DBugConfiguredAnchor<?> anchor) {
		if (theConnection == null)
			return null;
		DBCompiledConfiguredAnchor compiledConfiguredAnchor = new DBCompiledConfiguredAnchor(compiledAnchor, compiledConfig, anchor);
		DBug.queueAction(compiledConfiguredAnchor::persist);
		return compiledConfiguredAnchor;
	}

	@Override
	public DBCompiledEvent compileForEvent(DBCompiledConfiguredAnchor compiledAnchor, DBCompiledEventConfig compiledEventType,
		DBugEvent<?> event) {
		if (theConnection == null)
			return null;
		DBCompiledEvent compiledEvent = new DBCompiledEvent(compiledAnchor, compiledEventType, event);
		DBug.queueAction(compiledEvent::persist);
		return compiledEvent;
	}

	@Override
	public void eventOccurred(DBugConfigEvent<?> event, DBCompiledConfiguredAnchor compiledAnchor, DBCompiledEvent compiledEvent) {
		DBug.queueAction(() -> compiledEvent.persistConfigEvent(event));
	}

	@Override
	public Transaction eventBegun(DBugConfigEvent<?> event, DBCompiledConfiguredAnchor compiledAnchor, DBCompiledEvent compiledEvent) {
		DBug.queueAction(() -> compiledEvent.persistConfigEvent(event));
		return () -> {
			if (!compiledEvent.isEndWritten)
				DBug.queueAction(() -> compiledEvent.updateEndTime());
		};
	}

	@Override
	public void close() {
		if (theConnection != null) {
			try {
				theConnection.close();
			} catch (SQLException e) {
				System.err
					.println(getClass().getSimpleName() + ": Error shutting down " + getClass().getSimpleName() + " database connection");
				e.printStackTrace();
			}
		}
	}

	private boolean prepareStatements() {
		if (!doPrepareStatements()) {
			// Maybe this is the first run against the DB or the schema has changed. We'll try to create the dbug schema.
			if (!createSchema()) {
				System.err.println(getClass().getSimpleName() + " Could not create/update dbug schema. Cannot configure DB reporter.");
				return false;
			} else if (!doPrepareStatements())
				return false; // The reason was already printed
		}
		return true;
	}

	private boolean doPrepareStatements() {
		String sql = null;
		try {
			theSchemaInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug_Dbug_Schema("//
				+ "process, id, name) VALUES (" + theProcessId + ", ?, ?)");
			theAnchorTypeInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Anchor_Type("//
				+ "process, id, dbug_schema, class_name) VALUES (" + theProcessId + ", ?, ?, ?)");
			theAnchorFieldInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Anchor_Field("//
				+ "process, id, anchor_type, name, field_type) VALUES (" + theProcessId + ", ?, ?, ?, ?)");
			theEventTypeInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Event_Type("//
				+ "process, id, anchor_type, name) VALUES (" + theProcessId + ", ?, ?, ?)");
			theEventFieldInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Event_Field("//
				+ "process, id, event_type, name) VALUES (" + theProcessId + ", ?, ?, ?)");

			theConfigInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Config("//
				+ "process, id, anchor_type, config_id) VALUES (" + theProcessId + ", ?, ?, ?)");
			theConfigConditionUpdate = theConnection.prepareStatement(sql = "UPDATE dbug.Config SET condition=?"//
				+ " WHERE process=" + theProcessId + " AND id=?");
			theConfigValueInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Config_Value("//
				+ "process, id, config, name) VALUES (" + theProcessId + ", ?, ?, ?)");
			theConfigEventInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Config_Event("//
				+ "process, id, config, event_type) VALUES (" + theProcessId + ", ?, ?, ?)");
			theConfigEventValueInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Config_Event_Value("//
				+ "process, id, config_event, name) VALUES (" + theProcessId + ", ?, ?, ?)");

			theAnchorInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Anchor("//
				+ "process, id, anchor_type) VALUES (" + theProcessId + ", ?, ?)");
			theAnchorValueInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Anchor_Value("//
				+ "process, field, config_value, event, value_str) VALUES (" + theProcessId + ", ?, ?, ?, ?)");
			theAnchorConfigInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Anchor_Config_Instance("//
				+ "process, id, config, anchor) VALUES (" + theProcessId + ", ?, ?, ?)");

			theEventInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Event_Instance("//
				+ "process, id, event_type, anchor, start_time, end_time) VALUES("//
				+ theProcessId + ", ?, ?, ?, ?, ?)");
			theEventEndUpdate = theConnection.prepareStatement(sql = "UPDATE dbug.Event_Instance SET end_time=?"//
				+ " WHERE process=" + theProcessId + " AND id=?");
			theEventValueInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Event_Value("//
				+ "process, field, event_value, event, value_str) VALUES (" + theProcessId + ", ?, ?, ?, ?)");

			theEventConfigInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Event_Config_Instance("//
				+ "process, id, anchor, event) VALUES (" + theProcessId + ", ?, ?, ?)");
		} catch (SQLException e) {
			System.err.println(getClass().getSimpleName() + ": Could not prepare initial statements. Cannot configure DB reporter.");
			System.err.println("Offending statement was " + sql);
		}
		return true;
	}

	private boolean createSchema() {
		StringBuilder statement = new StringBuilder();
		try (BufferedReader sql = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("dbug.sql")));
			Statement stmt = theConnection.createStatement()) {
			boolean statementBeginning = true;
			for (int read = sql.read(); read > 0; read = sql.read()) {
				if (statementBeginning && Character.isWhitespace((char) read)) {
					continue;
				}
				statementBeginning = false;
				statement.append((char) read);
				if (read == ';') {
					stmt.execute(statement.toString());
					statement.setLength(0);
					statementBeginning = true;
				}
			}
		} catch (NullPointerException e2) {
			System.err
				.println(getClass().getSimpleName() + ": dbug.sql could not be found in the classpath. Cannot configure DB reporter.");
			return false;
		} catch (IOException e2) {
			System.err.println(getClass().getSimpleName() + ": Error reading dbug.sql. Cannot configure DB reporter.");
			e2.printStackTrace();
			return false;
		} catch (SQLException e2) {
			System.err.println(getClass().getSimpleName()
				+ ": Could not create dbug schema in DB. Cannot configure DB reporter.  Offending statement was:");
			System.err.println(statement);
			e2.printStackTrace();
			return false;
		}
		return true;
	}

	private boolean insertProcess() {
		String hostName;
		try {
			hostName = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			System.err.println(getClass().getSimpleName() + ": Could not determine local host name. Using 'unknown'.");
			e.printStackTrace();
			hostName = "unknown";
		}
		RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
		String runtimeName = runtimeBean.getName();
		int processId;
		try {
			processId = Integer.parseInt(runtimeName);
		} catch (NumberFormatException e) {
			System.err
				.println(getClass().getSimpleName() + ": Could not parse process ID from runtime name: " + runtimeName + ". Using 0.");
			processId = 0;
		}
		long startTime = runtimeBean.getStartTime();
		try (PreparedStatement stmt = theConnection
			.prepareStatement("INSERT INTO dbug.Process(host, process_id, start_time) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
			stmt.setString(1, hostName);
			stmt.setInt(2, processId);
			stmt.setDate(3, new Date(startTime));
			try (ResultSet genKeys = stmt.getGeneratedKeys()) {
				theProcessId = genKeys.getLong(1);
			}
		} catch (SQLException e) {
			System.err.println(getClass().getSimpleName() + " Could not insert process. Cannot configure DB reporter.");
			e.printStackTrace();
			return false;
		}
		return true;
	}

	static class DBCompiledAnchorType {
		final DBReporter theReporter;
		final DBugAnchorType<?> theAnchorType;
		final long schemaId;
		final long id;
		final boolean isNewSchema;
		final ParameterMap<Long> theStaticFieldIds;
		final ParameterMap<Long> theDynamicFieldIds;
		final ParameterMap<DBCompiledEventType> theEventTypes;

		DBCompiledAnchorType(DBReporter reporter, DBugAnchorType<?> anchorType) {
			theReporter = reporter;
			theAnchorType = anchorType;
			boolean[] newSchema = new boolean[1];
			schemaId = reporter.theSchemaIds.computeIfAbsent(anchorType.getSchema(), s -> {
				newSchema[0] = true;
				return reporter.theSchemaIdGen.getAndIncrement();
			});
			this.isNewSchema = newSchema[0];
			id = reporter.theAnchorTypeIdGen.getAndIncrement();

			theStaticFieldIds = anchorType.getStaticFields().keySet().createMap();
			theDynamicFieldIds = anchorType.getDynamicFields().keySet().createMap();
			for (int i = 0; i < theStaticFieldIds.keySet().size(); i++)
				theStaticFieldIds.put(i, reporter.theAnchorFieldIdGen.getAndIncrement());
			for (int i = 0; i < theDynamicFieldIds.keySet().size(); i++)
				theDynamicFieldIds.put(i, reporter.theAnchorFieldIdGen.getAndIncrement());
			theEventTypes = anchorType.getEventTypes().keySet().createMap();
			for (int i = 0; i < theEventTypes.keySet().size(); i++)
				theEventTypes.put(i, new DBCompiledEventType(this, i));
		}

		void persist() {
			if (isNewSchema) {
				synchronized (theReporter.theSchemaInsert) {
					try {
						theReporter.theSchemaInsert.setLong(1, schemaId);
						theReporter.theSchemaInsert.setString(2, theAnchorType.getSchema());
						theReporter.theSchemaInsert.execute();
					} catch (SQLException e) {
						System.err.println("Could not insert schema " + theAnchorType.getSchema());
						e.printStackTrace();
					}
				}
			}
			synchronized (theReporter.theAnchorTypeInsert) {
				try {
					theReporter.theAnchorTypeInsert.setLong(1, id);
					theReporter.theAnchorTypeInsert.setLong(2, schemaId);
					theReporter.theAnchorTypeInsert.setString(3, theAnchorType.getType().getName());
					theReporter.theAnchorTypeInsert.execute();
				} catch (SQLException e) {
					System.err.println("Could not insert anchor type " + theAnchorType);
					e.printStackTrace();
				}
			}
			synchronized (theReporter.theAnchorFieldInsert) {
				try {
					theReporter.theAnchorFieldInsert.setLong(2, id);
					theReporter.theAnchorFieldInsert.setInt(4, 0); // Static fields
					for (int i = 0; i < theStaticFieldIds.keySet().size(); i++) {
						theReporter.theAnchorFieldInsert.setLong(1, theStaticFieldIds.get(i));
						theReporter.theAnchorFieldInsert.setString(3, theStaticFieldIds.keySet().get(i));
						theReporter.theAnchorFieldInsert.execute();
					}
					theReporter.theAnchorFieldInsert.setInt(4, 1); // Dynamic fields
					for (int i = 0; i < theDynamicFieldIds.keySet().size(); i++) {
						theReporter.theAnchorFieldInsert.setLong(1, theDynamicFieldIds.get(i));
						theReporter.theAnchorFieldInsert.setString(3, theDynamicFieldIds.keySet().get(i));
						theReporter.theAnchorFieldInsert.execute();
					}
				} catch (SQLException e) {
					System.err.println("Could not insert anchor fields for " + theAnchorType);
					e.printStackTrace();
				}
			}
			try {
				synchronized (theReporter.theEventTypeInsert) {
					theReporter.theEventTypeInsert.setLong(1, id);
					for (int i = 0; i < theEventTypes.keySet().size(); i++)
						theEventTypes.get(i).persist();
				}
				synchronized (theReporter.theEventFieldInsert) {
					for (int i = 0; i < theEventTypes.keySet().size(); i++)
						theEventTypes.get(i).persistFields();
				}
			} catch (SQLException e) {
				System.err.println("Could not insert event types for " + theAnchorType);
				e.printStackTrace();
			}
		}
	}

	static class DBCompiledEventType {
		final DBCompiledAnchorType theAnchorType;
		final DBugEventType<?> theEventType;
		final long id;
		final ParameterMap<Long> theEventFieldIds;

		DBCompiledEventType(DBCompiledAnchorType anchorType, int eventIndex) {
			theAnchorType = anchorType;
			theEventType = anchorType.theAnchorType.getEventTypes().get(eventIndex);
			id = theAnchorType.theReporter.theEventTypeIdGen.getAndIncrement();
			theEventFieldIds = theEventType.getEventFields().keySet().createMap();
			for (int i = 0; i < theEventFieldIds.keySet().size(); i++)
				theEventFieldIds.put(i, theAnchorType.theReporter.theEventFieldIdGen.getAndIncrement());
		}

		void persist() throws SQLException {
			theAnchorType.theReporter.theEventTypeInsert.setLong(1, id);
			theAnchorType.theReporter.theEventTypeInsert.setString(3, theEventType.getEventName());
			theAnchorType.theReporter.theEventTypeInsert.execute();
		}

		void persistFields() throws SQLException {
			theAnchorType.theReporter.theEventFieldInsert.setLong(2, id);
			for (int i = 0; i < theEventFieldIds.keySet().size(); i++) {
				theAnchorType.theReporter.theEventFieldInsert.setLong(1, theEventFieldIds.get(i));
				theAnchorType.theReporter.theEventFieldInsert.setString(2, theEventFieldIds.keySet().get(i));
				theAnchorType.theReporter.theEventFieldInsert.execute();
			}
		}
	}

	static class DBCompiledAnchorConfig {
		final DBReporter theReporter;
		final DBCompiledAnchorType theAnchorType;
		final boolean isNewAnchorType;
		final DBugConfig<?> theAnchor;
		final long id;
		final ParameterMap<Long> theConfigValueIds;
		final long theConfigConditionId;
		final ParameterMap<List<DBCompiledEventConfig>> theEventConfigs;

		DBCompiledAnchorConfig(DBReporter reporter, DBugConfig<?> anchor) {
			theReporter = reporter;
			theAnchor = anchor;
			boolean[] newAnchorType = new boolean[1];
			theAnchorType = reporter.theAnchorTypes
				.computeIfAbsent(new BiTuple<>(anchor.getAnchorType().getSchema(), anchor.getAnchorType().getType().getName()), t -> {
					newAnchorType[0] = true;
					return new DBCompiledAnchorType(reporter, anchor.getAnchorType());
				});
			isNewAnchorType = newAnchorType[0];
			id = reporter.theConfigIdGen.getAndIncrement();
			theConfigValueIds = anchor.getValues().keySet().createMap();
			for (int i = 0; i < theConfigValueIds.keySet().size(); i++)
				theConfigValueIds.put(i, reporter.theConfigValueIdGen.getAndIncrement());
			theConfigConditionId = theReporter.theConfigValueIdGen.getAndIncrement();
			theEventConfigs = theAnchor.getEvents().keySet().createMap();
			for (int i = 0; i < theEventConfigs.keySet().size(); i++) {
				if (theAnchor.getEvents().get(i).isEmpty())
					theEventConfigs.put(i, Collections.emptyList());
				else
					theEventConfigs.put(i,
						theAnchor.getEvents().get(i).stream().map(e -> new DBCompiledEventConfig(this, e)).collect(Collectors.toList()));
			}
		}

		void persist() {
			if (isNewAnchorType)
				theAnchorType.persist();
			try {
				synchronized (theReporter.theConfigInsert) {
					theReporter.theConfigInsert.setLong(1, id);
					theReporter.theConfigInsert.setLong(2, theAnchorType.id);
					theReporter.theConfigInsert.setString(3, theAnchor.getTemplate().getID());
					theReporter.theConfigInsert.execute();
				}
				synchronized (theReporter.theConfigValueInsert) {
					theReporter.theConfigValueInsert.setLong(2, id);
					for (int i = 0; i < theConfigValueIds.keySet().size(); i++) {
						theReporter.theConfigValueInsert.setLong(1, theConfigValueIds.get(i));
						theReporter.theConfigValueInsert.setString(3, theConfigValueIds.keySet().get(i));
						theReporter.theConfigValueInsert.execute();
					}
					theReporter.theConfigValueInsert.setLong(1, theConfigConditionId);
					theReporter.theConfigValueInsert.setNull(3, Types.VARCHAR);
					theReporter.theConfigValueInsert.execute();

					theReporter.theConfigConditionUpdate.setLong(1, theConfigConditionId);
					theReporter.theConfigConditionUpdate.setLong(2, id);
					theReporter.theConfigConditionUpdate.executeUpdate();
				}
				synchronized (theReporter.theEventConfigInsert) {
					theReporter.theConfigEventInsert.setLong(2, id);
					for (int i = 0; i < theEventConfigs.keySet().size(); i++) {
						theReporter.theConfigEventInsert.setLong(3, theAnchorType.theEventTypes.get(i).id);
						for (DBCompiledEventConfig evtConfig : theEventConfigs.get(i))
							evtConfig.persist();
					}
				}
				synchronized (theReporter.theEventValueInsert) {
					for (int i = 0; i < theEventConfigs.keySet().size(); i++) {
						for (DBCompiledEventConfig evtConfig : theEventConfigs.get(i))
							evtConfig.persistValues();
					}
				}
			} catch (SQLException e) {
				System.err.println("Could not insert anchor config for " + theAnchor);
				e.printStackTrace();
			}
		}
	}

	static class DBCompiledEventConfig {
		final DBCompiledAnchorConfig theAnchor;
		final DBCompiledEventType theEventType;
		final DBugEventConfig<?> theEventConfig;
		final long id;
		final ParameterMap<Long> theEventConfigValueIds;
		final long theEventConditionId;

		DBCompiledEventConfig(DBCompiledAnchorConfig compiledAnchor, DBugEventConfig<?> event) {
			theAnchor = compiledAnchor;
			theEventType = compiledAnchor.theAnchorType.theEventTypes.get(event.eventType.getEventIndex());
			theEventConfig = event;
			id = compiledAnchor.theReporter.theConfigEventIdGen.getAndIncrement();
			theEventConfigValueIds = event.eventValues.keySet().createMap();
			for (int i = 0; i < theEventConfigValueIds.keySet().size(); i++)
				theEventConfigValueIds.put(i, compiledAnchor.theReporter.theConfigEventValueIdGen.getAndIncrement());
			theEventConditionId = compiledAnchor.theReporter.theConfigEventValueIdGen.getAndIncrement();
		}

		void persist() throws SQLException {
			theAnchor.theReporter.theConfigEventInsert.setLong(1, id);
			theAnchor.theReporter.theConfigEventInsert.execute();
		}

		void persistValues() throws SQLException {
			theAnchor.theReporter.theConfigEventValueInsert.setLong(2, id);
			for (int i = 0; i < theEventConfigValueIds.keySet().size(); i++) {
				theAnchor.theReporter.theConfigEventValueInsert.setLong(1, theEventConfigValueIds.get(i));
				theAnchor.theReporter.theConfigEventValueInsert.setString(1, theEventConfigValueIds.keySet().get(i));
				theAnchor.theReporter.theConfigEventValueInsert.execute();
			}
		}
	}

	static class DBCompiledAnchor {
		final DBReporter theReporter;
		final DBCompiledAnchorType theAnchorType;
		final DBugAnchor<?> theAnchor;
		final long id;
		final ParameterMap<Object> theStaticAnchorFieldValues;
		final ParameterMap<Object> theDynamicAnchorFieldValues;
		final AtomicBoolean isInitialized;

		DBCompiledAnchor(DBReporter reporter, DBugAnchor<?> anchor) {
			theReporter = reporter;
			// Should have already been populated by the compileForAnchorConfig method
			theAnchorType = reporter.theAnchorTypes.get(new BiTuple<>(anchor.getType().getSchema(), anchor.getType().getType().getName()));
			theAnchor = anchor;
			id = reporter.theAnchorIdGen.getAndIncrement();
			theStaticAnchorFieldValues = anchor.getType().getStaticFields().keySet().createMap();
			theDynamicAnchorFieldValues = anchor.getType().getDynamicFields().keySet().createMap();
			isInitialized = new AtomicBoolean();
		}

		void persist() {
			synchronized (theReporter.theAnchorInsert) {
				try {
					theReporter.theAnchorInsert.setLong(1, id);
					theReporter.theAnchorInsert.setLong(2, theAnchorType.id);
					theReporter.theAnchorInsert.execute();
				} catch (SQLException e) {
					System.err.println("Could not insert anchor " + theAnchorType.theAnchorType + " " + theAnchor);
					e.printStackTrace();
				}
			}
		}

		void checkValues(DBugEvent<?> event) throws SQLException {
			if (!isInitialized.compareAndSet(false, true)) {
				for (int i = 0; i < theStaticAnchorFieldValues.keySet().size(); i++) {
					Object fieldValue = theAnchor.getStaticValues().get(i);
					theStaticAnchorFieldValues.put(i, fieldValue);
					writeField(true, i, event.getEventId(), fieldValue);
				}
				for (int i = 0; i < theDynamicAnchorFieldValues.keySet().size(); i++) {
					Object fieldValue = event.getDynamicValues().get(i);
					theDynamicAnchorFieldValues.put(i, fieldValue);
					writeField(true, i, event.getEventId(), fieldValue);
				}
			} else {
				for (int i = 0; i < theStaticAnchorFieldValues.keySet().size(); i++) {
					Object fieldValue = theAnchor.getStaticValues().get(i);
					if (!Objects.equals(theStaticAnchorFieldValues.get(i), fieldValue)) {
						theStaticAnchorFieldValues.put(i, fieldValue);
						writeField(true, i, event.getEventId(), fieldValue);
					}
				}
				for (int i = 0; i < theDynamicAnchorFieldValues.keySet().size(); i++) {
					Object fieldValue = event.getDynamicValues().get(i);
					if (!Objects.equals(theDynamicAnchorFieldValues.get(i), fieldValue)) {
						theDynamicAnchorFieldValues.put(i, fieldValue);
						writeField(true, i, event.getEventId(), fieldValue);
					}
				}
			}
		}

		private void writeField(boolean staticField, int index, long eventId, Object fieldValue) throws SQLException {
			theReporter.theAnchorValueInsert.setLong(1,
				(staticField ? theAnchorType.theStaticFieldIds : theAnchorType.theDynamicFieldIds).get(index));
			theReporter.theAnchorValueInsert.setNull(2, Types.BIGINT);
			theReporter.theAnchorValueInsert.setLong(3, eventId);
			theReporter.theAnchorValueInsert.setString(4, valueString(fieldValue));
			theReporter.theAnchorValueInsert.execute();
		}
	}

	static class DBCompiledConfiguredAnchor {
		final DBCompiledAnchor theCompiledAnchor;
		final DBCompiledAnchorConfig theAnchorConfig;
		final DBugConfiguredAnchor<?> theAnchor;
		final long id;
		final ParameterMap<Object> theConfigAnchorValues;
		boolean isActive;
		final AtomicBoolean isInitialized;

		DBCompiledConfiguredAnchor(DBCompiledAnchor compiledAnchor, DBCompiledAnchorConfig compiledAnchorConfig,
			DBugConfiguredAnchor<?> anchor) {
			theCompiledAnchor = compiledAnchor;
			theAnchorConfig = compiledAnchorConfig;
			theAnchor = anchor;
			id = compiledAnchorConfig.theReporter.theAnchorConfigIdGen.getAndIncrement();
			theConfigAnchorValues = compiledAnchorConfig.theConfigValueIds.keySet().createMap();
			isInitialized = new AtomicBoolean();
		}

		void persist() {
			synchronized (theCompiledAnchor.theReporter.theAnchorConfigInsert) {
				try {
					theCompiledAnchor.theReporter.theAnchorConfigInsert.setLong(1, id);
					theCompiledAnchor.theReporter.theAnchorConfigInsert.setLong(2, theAnchorConfig.id);
					theCompiledAnchor.theReporter.theAnchorConfigInsert.setLong(3, theCompiledAnchor.id);
					theCompiledAnchor.theReporter.theAnchorConfigInsert.execute();
				} catch (SQLException e) {
					System.err.println("Could not persist anchor " + theCompiledAnchor.theAnchorType.theAnchorType + " " + theAnchor);
					e.printStackTrace();
				}
			}
		}

		void checkValues(DBugConfigEvent<?> event) {
			synchronized (theCompiledAnchor.theReporter.theAnchorValueInsert) {
				try {
					theCompiledAnchor.checkValues(event);
					if (!isInitialized.compareAndSet(false, true)) {
						for (int i = 0; i < theConfigAnchorValues.keySet().size(); i++) {
							Object fieldValue = event.getEventConfigValues().get(i);
							theConfigAnchorValues.put(i, fieldValue);
							writeConfigValue(i, event.getEventId(), fieldValue);
						}
						isActive = theAnchor.isActive();
						theCompiledAnchor.theReporter.theAnchorValueInsert.setNull(1, Types.BIGINT);
						theCompiledAnchor.theReporter.theAnchorValueInsert.setLong(2, theAnchorConfig.theConfigConditionId);
						theCompiledAnchor.theReporter.theAnchorValueInsert.setLong(3, event.getEventId());
						theCompiledAnchor.theReporter.theAnchorValueInsert.setString(4, valueString(isActive));
						theCompiledAnchor.theReporter.theAnchorValueInsert.execute();
					} else {
						for (int i = 0; i < theConfigAnchorValues.keySet().size(); i++) {
							Object fieldValue = event.getDynamicValues().get(i);
							if (!Objects.equals(theConfigAnchorValues.get(i), fieldValue)) {
								theConfigAnchorValues.put(i, fieldValue);
								writeConfigValue(i, event.getEventId(), fieldValue);
							}
						}
						if (isActive != theAnchor.isActive()) {
							isActive = !isActive;
							theCompiledAnchor.theReporter.theAnchorValueInsert.setLong(1, theAnchorConfig.theConfigConditionId);
							theCompiledAnchor.theReporter.theAnchorValueInsert.setNull(2, Types.BIGINT);
							theCompiledAnchor.theReporter.theAnchorValueInsert.setLong(3, event.getEventId());
							theCompiledAnchor.theReporter.theAnchorValueInsert.setString(4, valueString(isActive));
							theCompiledAnchor.theReporter.theAnchorValueInsert.execute();
						}
					}
				} catch (SQLException e) {
					System.err.println("Could not persist field/config value updates for anchor "
						+ theCompiledAnchor.theAnchorType.theAnchorType + " " + theAnchor);
					e.printStackTrace();
				}
			}
		}

		private void writeConfigValue(int index, long eventId, Object fieldValue) throws SQLException {
			theCompiledAnchor.theReporter.theAnchorValueInsert.setNull(1, Types.BIGINT);
			theCompiledAnchor.theReporter.theAnchorValueInsert.setLong(2, theAnchorConfig.theConfigValueIds.get(index));
			theCompiledAnchor.theReporter.theAnchorValueInsert.setLong(3, eventId);
			theCompiledAnchor.theReporter.theAnchorValueInsert.setString(4, valueString(fieldValue));
			theCompiledAnchor.theReporter.theAnchorValueInsert.execute();
		}
	}

	static String valueString(Object value) {
		String valueStr = String.valueOf(value);
		if (valueStr.length() > 512)
			valueStr = valueStr.substring(0, 509) + "...";
		return valueStr;
	}

	static class DBCompiledEvent {
		final DBCompiledConfiguredAnchor theCompiledAnchor;
		final DBCompiledEventConfig theEventConfig;
		final DBugEvent<?> theEvent;
		boolean isEndWritten;

		DBCompiledEvent(DBCompiledConfiguredAnchor compiledAnchor, DBCompiledEventConfig compiledEventType, DBugEvent<?> event) {
			theCompiledAnchor = compiledAnchor;
			theEventConfig = compiledEventType;
			theEvent = event;
		}

		void persist() {
			try {
				synchronized (theCompiledAnchor.theCompiledAnchor.theReporter.theEventInsert) {
					theCompiledAnchor.theCompiledAnchor.theReporter.theEventInsert.setLong(1, theEvent.getEventId());
					theCompiledAnchor.theCompiledAnchor.theReporter.theEventInsert.setLong(2, theEventConfig.theEventType.id);
					theCompiledAnchor.theCompiledAnchor.theReporter.theEventInsert.setLong(3, theCompiledAnchor.theCompiledAnchor.id);
					theCompiledAnchor.theCompiledAnchor.theReporter.theEventInsert.setLong(4,
						theCompiledAnchor.theCompiledAnchor.theReporter.theThreadIds.get());
					theCompiledAnchor.theCompiledAnchor.theReporter.theEventInsert.setDate(5, new Date(theEvent.getStart().toEpochMilli()));
					if (theEvent.getEnd() != null)
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventInsert.setDate(6,
							new Date(theEvent.getEnd().toEpochMilli()));
					else
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventInsert.setNull(6, Types.TIMESTAMP);
					theCompiledAnchor.theCompiledAnchor.theReporter.theEventInsert.execute();
				}
				synchronized (theCompiledAnchor.theCompiledAnchor.theReporter.theEventValueInsert) {
					for (int i = 0; i < theEvent.getEventValues().keySet().size(); i++) {
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventValueInsert.setLong(1,
							theEventConfig.theEventType.theEventFieldIds.get(i));
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventValueInsert.setNull(2, Types.BIGINT);
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventValueInsert.setLong(3, theEvent.getEventId());
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventValueInsert.setString(4,
							valueString(theEvent.getEventValues().get(i)));
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventValueInsert.execute();
					}
				}
			} catch (SQLException e) {
				System.err.println("Could not persist event");
				e.printStackTrace();
			}
		}

		void persistConfigEvent(DBugConfigEvent<?> event) {
			try {
				long configEventId = theCompiledAnchor.theCompiledAnchor.theReporter.theEventConfigIdGen.getAndIncrement();
				synchronized (theCompiledAnchor.theCompiledAnchor.theReporter.theEventConfigInsert) {
					theCompiledAnchor.theCompiledAnchor.theReporter.theEventConfigInsert.setLong(1, configEventId);
					theCompiledAnchor.theCompiledAnchor.theReporter.theEventConfigInsert.setLong(2, theCompiledAnchor.id);
					theCompiledAnchor.theCompiledAnchor.theReporter.theEventConfigInsert.setLong(3, theEvent.getEventId());
					theCompiledAnchor.theCompiledAnchor.theReporter.theEventConfigInsert.execute();
				}
				synchronized (theCompiledAnchor.theCompiledAnchor.theReporter.theEventValueInsert) {
					for (int i = 0; i < theEventConfig.theEventConfigValueIds.keySet().size(); i++) {
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventValueInsert.setNull(1, Types.BIGINT);
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventValueInsert.setLong(2,
							theEventConfig.theEventConfigValueIds.get(i));
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventValueInsert.setLong(3, theEvent.getEventId());
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventValueInsert.setString(4,
							valueString(event.getEventConfigValues().get(i)));
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventValueInsert.execute();
					}
				}
			} catch (SQLException e) {
				System.err.println("Could not persist configured event");
				e.printStackTrace();
			}
		}

		void updateEndTime() {
			if (!isEndWritten) {
				isEndWritten = true;
				synchronized (theCompiledAnchor.theCompiledAnchor.theReporter.theEventEndUpdate) {
					try {
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventEndUpdate.setDate(1,
							new Date(theEvent.getEnd().toEpochMilli()));
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventEndUpdate.setLong(2, theEvent.getEventId());
						theCompiledAnchor.theCompiledAnchor.theReporter.theEventEndUpdate.executeUpdate();
					} catch (SQLException e) {
						System.err.println("Could not update event end time");
						e.printStackTrace();
					}
				}
			}
		}
	}
}
