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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dbug.DBug;
import org.dbug.DBugAnchorType;
import org.dbug.config.DBugConfig;
import org.dbug.config.DBugConfig.DBugEventConfig;
import org.dbug.config.DBugConfigEvent;
import org.dbug.config.DBugConfiguredAnchor;
import org.dbug.config.DBugEventReporter;
import org.qommons.BiTuple;
import org.qommons.Transaction;
import org.qommons.config.QommonsConfig;

public class DBReporter
	implements
	DBugEventReporter<DBReporter.DBCompiledAnchorConfig, DBReporter.DBCompiledEventType, DBReporter.DBCompiledAnchor, DBReporter.DBCompiledEvent> {
	private Connection theConnection;

	long theProcessId;
	final Map<String, Long> theSchemaIds;
	final Map<BiTuple<String, String>, DBCompiledAnchorType> theAnchorTypes;

	PreparedStatement theSchemaInsert;
	PreparedStatement theAnchorTypeInsert;
	PreparedStatement theAnchorFieldInsert;
	PreparedStatement theEventTypeInsert;
	PreparedStatement theEventFieldInsert;
	PreparedStatement theConfigInsert;
	PreparedStatement theConfigVariableInsert;
	PreparedStatement theConfigEventInsert;
	PreparedStatement theConfigEventVariableInsert;
	PreparedStatement theAnchorInsert;
	PreparedStatement theAnchorConfigInsert;
	PreparedStatement theEventInsert;
	PreparedStatement theEventValueInsert;
	PreparedStatement theEventEndUpdate;
	PreparedStatement theEventConfigInsert;
	PreparedStatement theAnchorValueInsert;

	public DBReporter() {
		theSchemaIds = new ConcurrentHashMap<>();
		theAnchorTypes = new ConcurrentHashMap<>();
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
	public DBCompiledEventType compileForEventType(DBCompiledAnchorConfig compiledAnchorType, DBugEventConfig<?> event) {
		if (theConnection == null)
			return null;
		DBCompiledEventType compiledEvent = new DBCompiledEventType(this, compiledAnchorType, event);
		DBug.queueAction(compiledEvent::persist);
		return compiledEvent;
	}

	@Override
	public DBCompiledAnchor compileForConfiguredAnchor(DBCompiledAnchorConfig compiledAnchorType, DBugConfiguredAnchor<?> anchor) {
		if (theConnection == null)
			return null;
		DBCompiledAnchor compiledAnchor = new DBCompiledAnchor(this, compiledAnchorType, anchor);
		DBug.queueAction(compiledAnchor::persist);
		return compiledAnchor;
	}

	@Override
	public DBCompiledEvent compileForEvent(DBCompiledAnchor compiledAnchor, DBCompiledEventType compiledEventType, long eventId) {
		if (theConnection == null)
			return null;
		DBCompiledEvent compiledEvent = new DBCompiledEvent(this, compiledAnchor, compiledEventType, eventId);
		DBug.queueAction(compiledEvent::persist);
		return compiledEvent;
	}

	@Override
	public void eventOccurred(DBugConfigEvent<?> event, DBCompiledAnchor compiledAnchor, DBCompiledEvent compiledEvent) {
		DBug.queueAction(() -> writeEvent(event, compiledAnchor, compiledEvent));
	}

	@Override
	public Transaction eventBegun(DBugConfigEvent<?> event, DBCompiledAnchor compiledAnchor, DBCompiledEvent compiledEvent) {
		long[] eventId = new long[] { -1 };
		DBug.queueAction(() -> eventId[0] = writeEvent(event, compiledAnchor, compiledEvent));
		return () -> {
			DBug.queueAction(() -> updateEventEnd(eventId, event));
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
			if (!writeSchema()) {
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
			theEventInsert = theConnection.prepareStatement(sql = "INSERT INTO dbug.Event_Instance("//
				+ "process, id, event_type, anchor, start_time, end_time) VALUES("//
				+ theProcessId + "?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
		} catch (SQLException e) {
			System.err.println(getClass().getSimpleName() + ": Could not prepare initial statements. Cannot configure DB reporter.");
			System.err.println("Offending statement was " + sql);
		}
		return true;
	}

	private boolean writeSchema() {
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

	long writeEvent(DBugConfigEvent<?> event, DBCompiledAnchor compiledAnchor, DBCompiledEvent compiledEvent) {
		long configEventId; // TODO gen event Id
		synchronized (theEventConfigInsert) {
			theEventConfigInsert.setLong(1, theProcessId);
			theEventConfigInsert.setLong(2, event.getEventId());
			theEventConfigInsert.setLong(3, compiledAnchor.configId);
			theEventConfigInsert.setLong(4, configEventId);
			theEventConfigInsert.execute();
			try (ResultSet genKeys = theEventConfigInsert.getGeneratedKeys()) {
				return genKeys.getLong(1);
			}
		}
	}

	void updateEventEnd(long [] eventId, DBugConfigEvent<?> event){
		if(eventId[0]==-1)
			DBug.queueAction(()->updateEventEnd(eventId, event));
		else{
			synchronized (theEventEndUpdate) {
				try {
					theEventEndUpdate.setLong(1, eventId[0]);
					theEventEndUpdate.setDate(2, new Date(event.getEnd().toEpochMilli()));
					theEventEndUpdate.executeUpdate();
				} catch (SQLException e) {
					System.err.println("Could not update event end time");
					e.printStackTrace();
				}
			}
		}
	}

	static class DBCompiledAnchorType {
		DBCompiledAnchorType(DBReporter reporter, DBugAnchorType<?> anchorType) {
			// TODO
		}

		void persist() {
			// TODO
		}
	}

	static class DBCompiledAnchorConfig {
		DBCompiledAnchorConfig(DBReporter reporter, DBugConfig<?> anchor) {
			// TODO Auto-generated constructor stub
		}

		void persist() {
			// TODO
		}
	}

	static class DBCompiledAnchor {
		DBCompiledAnchor(DBReporter reporter, DBCompiledAnchorConfig compiledAnchorType, DBugConfiguredAnchor<?> anchor) {
			// TODO Auto-generated constructor stub
		}

		void persist() {
			// TODO
		}
	}

	static class DBCompiledEventType {
		DBCompiledEventType(DBReporter reporter, DBCompiledAnchorConfig compiledAnchor, DBugEventConfig<?> event) {
			// TODO Auto-generated constructor stub
		}

		void persist() {
			// TODO
		}
	}

	static class DBCompiledEvent {
		DBCompiledEvent(DBReporter reporter, DBCompiledAnchorConfig compiledAnchor, DBugEventConfig<?> event) {
			// TODO Auto-generated constructor stub
		}

		void persist() {
			// TODO
		}
	}
}
