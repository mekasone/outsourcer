import java.util.Map;
import java.sql.*;
import java.net.*;
import java.io.*;
import java.util.ArrayList;

public class CustomSQLModel
{
	String id;
	String tableName;
	ArrayList<String> columns;
	ArrayList<String> columnDataTypes;
	String sqlText;
	String sourceType;
	String sourceServerName;
	String sourceInstanceName;
	String sourcePort;
	String sourceDatabaseName;
	String sourceUserName;
	String sourcePass;
	String gpfdistPort;
	public static int maxColumns = 50;
	
	public static ResultSet getList(String search, String limit, String offset, String sortBy, String sort) throws SQLException
	{
		String strSQL = "SELECT '<button onclick=\"updateCustomSQL(''' || id || ''', ''update'')\">Update</button>' ||\n";
		strSQL += "'&nbsp;<button onclick=\"updateCustomSQL(''' || id || ''', ''delete'')\">Delete</button>' AS manage,\n";
		strSQL += "id,\n";
		strSQL += "LOWER(table_name) AS table_name, initcap(source_type) as source_type\n";
		strSQL += "FROM os.custom_sql\n";
		
		if (!search.equals(""))
		{
			strSQL += "WHERE LOWER(table_name) LIKE '%' || LOWER('" + search + "') || '%'\n";
			strSQL += "OR LOWER(source_type) LIKE '%' || LOWER('" + search + "') || '%'\n";
		}
		sortBy = sortBy.toLowerCase();
		if (sortBy.equals("table_name") || sortBy.equals("source_type"))
			strSQL += "ORDER BY " + sortBy + " " + sort + "\n";
		else
			strSQL += "ORDER BY LOWER(table_name) ASC\n";

		if (!limit.equals(""))
			strSQL += "LIMIT " + limit + " ";

		if (!offset.equals(""))
			strSQL += "OFFSET " + offset;

		try
		{
			ResultSet rs = OutsourcerModel.getResults(strSQL);
			return rs;
		}
		catch (SQLException ex)
		{
			throw new SQLException(ex.getMessage());
		}
	}

	private static void customStop(String id) throws SQLException
	{
		String gpfdistPort = "0";
		try
		{
			String strSQL = "SELECT os.fn_customstop('" + OSProperties.osServer + "', " +  OSProperties.osPort + ", " + id + ")";
			OutsourcerModel.getResults(strSQL);
		}
		catch (SQLException ex)
		{
			throw new SQLException(ex.getMessage());
		}
	}

	private static String customStart() throws SQLException
	{
		String gpfdistPort = "0";
		try
		{
			String strSQL = "SELECT gpfdist_port FROM os.customstart";
			ResultSet rs = OutsourcerModel.getResults(strSQL);
			while (rs.next())
			{
				gpfdistPort  = rs.getString(1);
			}

			return gpfdistPort;
		}
		catch (SQLException ex)
		{
			throw new SQLException(ex.getMessage());
		}
	}

	public static void updateTable(String id, String tableName, ArrayList<String> columns, ArrayList<String> columnDataTypes, String sqlText, String sourceType, String sourceServerName, String sourceInstanceName, String sourcePort, String sourceDatabaseName, String sourceUserName, String sourcePass) throws SQLException
	{
		String strColumns = OutsourcerModel.javaArrayToString(columns);
		String strColumnDataTypes = OutsourcerModel.javaArrayToString(columnDataTypes);
		sqlText = OutsourcerModel.setSQLString(sqlText);
		sourceType = OutsourcerModel.setSQLString(sourceType);
		sourceServerName = OutsourcerModel.setSQLString(sourceServerName);
		sourceInstanceName = OutsourcerModel.setSQLString(sourceInstanceName);
		sourcePort = OutsourcerModel.setSQLInt(sourcePort);
		sourceDatabaseName = OutsourcerModel.setSQLString(sourceDatabaseName);
		sourceUserName = OutsourcerModel.setSQLString(sourceUserName);
		sourcePass = OutsourcerModel.setSQLString(sourcePass);

		try
		{
			//stop the gpfdist process for this job
			customStop(id);

			//drop the old version of the table
			tableName = tableName.toLowerCase();
			dropExtTable(tableName);

			//add the quotes for the table name so the insert works properly
			tableName = OutsourcerModel.setSQLString(tableName);

			//dynamically get the gpfdist port for the custom table
			String gpfdistPort = customStart();

			//build the insert statement
			String strSQL = "INSERT INTO os.ao_custom_sql\n";
			strSQL += "(id, table_name, columns, column_datatypes, sql_text, source_type, source_server_name, source_instance_name, source_port, source_database_name, source_user_name, source_pass, gpfdist_port)\n";
			strSQL += "VALUES\n";
			strSQL += "	(" + id + ", \n";
			strSQL += "     " + tableName + ", \n";
			strSQL += "     " + strColumns + ", \n";
			strSQL += "     " + strColumnDataTypes + ", \n";
			strSQL += "     " + sqlText + ", \n";
			strSQL += "     " + sourceType + ", \n";
			strSQL += "     " + sourceServerName + ", \n";
			strSQL += "     " + sourceInstanceName + ", \n";
			strSQL += "     " + sourcePort + ", \n";
			strSQL += "     " + sourceDatabaseName + ", \n";
			strSQL += "     " + sourceUserName + ", \n";
			strSQL += "     " + sourcePass + ", \n";
			strSQL += "     " + gpfdistPort + ")\n";
			OutsourcerModel.updateTable(strSQL);

			//create the external table
			createExtTable(id);
		}
		catch (SQLException ex)
		{
			throw new SQLException(ex.getMessage());
		}
	}

	public static void deleteTable(String id, String tableName) throws SQLException
	{

		String strSQL = "INSERT INTO os.ao_custom_sql\n";
		strSQL += "(id, table_name, columns, column_datatypes, sql_text, source_type, source_server_name, source_instance_name, source_port, source_database_name, source_user_name, source_pass, gpfdist_port, deleted)\n";
		strSQL += "SELECT id, table_name, columns, column_datatypes, sql_text, source_type, source_server_name, source_instance_name, source_port, source_database_name, source_user_name, source_pass, gpfdist_port, TRUE AS deleted\n";
		strSQL += "FROM os.custom_sql\n";
		strSQL += "WHERE id = " + id;
		try
		{
			//stop the gpfdist process for this job
			customStop(id);

			tableName = tableName.toLowerCase();
			dropExtTable(tableName);
			OutsourcerModel.updateTable(strSQL);
		}

		catch (SQLException ex)
		{
			throw new SQLException(ex.getMessage());
		}
	}
	
	public static void insertTable(String tableName, ArrayList<String> columns, ArrayList<String> columnDataTypes, String sqlText, String extConnectionId) throws SQLException
	{
		tableName = OutsourcerModel.setSQLString(tableName.toLowerCase());
		String strColumns = OutsourcerModel.javaArrayToString(columns);
		String strColumnDataTypes = OutsourcerModel.javaArrayToString(columnDataTypes);
		sqlText = OutsourcerModel.setSQLString(sqlText);

		try
		{
			String id = nextVal();

			//dynamically get the gpfdist port for the custom table
			String gpfdistPort = customStart();

			String strSQL = "INSERT INTO os.ao_custom_sql\n";
			strSQL += "(id, table_name, columns, column_datatypes, sql_text, gpfdist_port,\n";
			strSQL += "source_type, source_server_name, source_instance_name, source_port, source_database_name, source_user_name, source_pass)\n";
			strSQL += "SELECT " + id + ", " + tableName + ", " + strColumns + ", " + strColumnDataTypes + ", " + sqlText + ", " + gpfdistPort + ",\n";
			strSQL += "source_type, source_server_name, source_instance_name, source_port, source_database_name, source_user_name, source_pass\n";
			strSQL += "FROM os.ext_connection\n";
			strSQL += "WHERE id = " + extConnectionId;
			OutsourcerModel.updateTable(strSQL);

			createExtTable(id);
			
		}
		catch (SQLException ex)
		{
			throw new SQLException(ex.getMessage());
		}
	}

	public static ArrayList<String> getDataTypes()
	{
		ArrayList<String> dataTypes = new ArrayList<String>();
		dataTypes.add("bigint");
		dataTypes.add("boolean");
		dataTypes.add("float8");
		dataTypes.add("int");
		dataTypes.add("numeric");
		dataTypes.add("smallint");
		dataTypes.add("text");
		dataTypes.add("timestamp");
		dataTypes.add("timestamptz");
		dataTypes.add("varchar");
		return dataTypes;
	}

	private static String nextVal() throws SQLException
	{
		try
		{
			String id = "";
			String strSQL = "SELECT nextval('os.ao_custom_sql_id_seq')";
			OutsourcerModel.getResults(strSQL);

			ResultSet rs = OutsourcerModel.getResults(strSQL);
			while (rs.next())
			{
				id = Integer.toString(rs.getInt(1));
			}

			return id;
	
		}
		catch (SQLException ex)
		{
			throw new SQLException(ex.getMessage());
		}
	}

	//refactor in the future to not use a function
	private static void createExtTable(String id) throws SQLException
	{
		try
		{
			String strSQL = "SELECT os.fn_create_ext_table(" + id + ", '" + OSProperties.osServer + "')"; 
			ResultSet rs = OutsourcerModel.getResults(strSQL);
		}
		catch (SQLException ex)
		{
			throw new SQLException(ex.getMessage());
		}
	}

	private static void dropExtTable(String tableName) throws SQLException
	{
		try
		{
			String strSQL = "DROP EXTERNAL TABLE IF EXISTS " + tableName;
			OutsourcerModel.updateTable(strSQL);

		}
		catch (SQLException ex)
		{
			throw new SQLException(ex.getMessage());
		}
	}

	public CustomSQLModel (String aId) throws SQLException
	{
		String strSQL = "SELECT id, table_name, columns, column_datatypes, sql_text, source_type, source_server_name, source_instance_name, source_port, source_database_name, source_user_name, source_pass\n";
		strSQL += "FROM os.custom_sql\n";
		strSQL += "WHERE id = " + aId;

		try
		{
			ResultSet rs = OutsourcerModel.getResults(strSQL);
			while (rs.next())
			{
				id = Integer.toString(rs.getInt(1));
				tableName = rs.getString(2);
				columns = OutsourcerModel.gpArrayToJavaArray(rs.getString(3));
				columnDataTypes = OutsourcerModel.gpArrayToJavaArray(rs.getString(4));
				sqlText = rs.getString(5);
				sourceType = rs.getString(6);
				sourceServerName = rs.getString(7);
				sourceInstanceName = rs.getString(8);
				sourcePort = rs.getString(9);
				sourceDatabaseName = rs.getString(10);
				sourceUserName = rs.getString(11);
				sourcePass = rs.getString(12);
			}
		}
		catch (SQLException ex)
		{
			//do something??
		}

	}

	public static CustomSQLModel getModel(String id)
	{
		try
		{
			return new CustomSQLModel(id);
		}
		catch (Exception ex)
		{
			return null;
		}
	}
}
