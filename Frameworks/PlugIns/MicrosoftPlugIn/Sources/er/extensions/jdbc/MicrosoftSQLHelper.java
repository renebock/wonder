package er.extensions.jdbc;

import java.sql.Types;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.jdbcadaptor.JDBCAdaptor;

import er.extensions.foundation.ERXProperties;

public class MicrosoftSQLHelper extends ERXSQLHelper.MicrosoftSQLHelper {

	/**
	 * If you want to change the default java.jdbc.Typs to SQL datatype mapping, you can specify them in the Properties
	 * file. EXAMPLE: er.extensions.ERXSQLHelper.Microsoft.jdbcType.VARCHAR=nvarchar If these properties are not set, we
	 * pass it up to the default impl.
	 * 
	 * @param adaptor
	 *            the adaptor to retrieve an external type for
	 * @param jdbcType
	 *            the JDBC type number
	 * @return a guess at the external type name to use
	 */
	@Override

	public String externalTypeForJDBCType(JDBCAdaptor adaptor, int jdbcType) {
		if (log.isDebugEnabled()) log.debug("request externalTypeForJDBCType: " + jdbcType);
		String externalType = ERXProperties.stringForKey("er.extensions.ERXSQLHelper.Microsoft.jdbcType." + jdbcType);
		if(externalType == null) {
			if(jdbcType == Types.BLOB) {
				externalType = "image";
			}
			else{
				externalType = super.externalTypeForJDBCType(adaptor, jdbcType);
			}
		}
		
		if (log.isDebugEnabled()) log.debug("externalTypeForJDBCType " + jdbcType + " = " + externalType);
	
		return externalType;
	}

	@Override
	public String sqlForCreateIndex(String indexName, String tableName, ColumnIndex... columnIndexes) {
		NSMutableArray<String> columnNames = columnNamesFromColumnIndexes(columnIndexes);
		return "CREATE INDEX \""+indexName+"\" ON \""+tableName+"\" (\""+new NSArray<String>(columnNames).componentsJoinedByString("\", \"")+"\")";
	}
	
	/**
	 * Returns the JDBCType that should be used for a varcharLarge column in migrations.
	 * 
	 * @return the JDBCType that should be used for a varcharLarge column in migrations
	 */
	@Override
	public int varcharLargeJDBCType() {
		return -16; // NTEXT
//		return Types.LONGVARCHAR;
	}
	
	/**
	 * Returns the width that should be used for a varcharLarge column in migrations.
	 * 
	 * @return the width that should be used for a varcharLarge column in migrations
	 */
	@Override
	public int varcharLargeColumnWidth() {
		return -1;
	}
	
}
