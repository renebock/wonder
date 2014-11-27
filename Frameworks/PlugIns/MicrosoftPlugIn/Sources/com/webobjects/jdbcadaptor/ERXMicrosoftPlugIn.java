package com.webobjects.jdbcadaptor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;

import com.webobjects.eoaccess.EOAdaptor;
import com.webobjects.eoaccess.EOAttribute;
import com.webobjects.eoaccess.EOEntity;
import com.webobjects.eoaccess.EOModelGroup;
import com.webobjects.eoaccess.EOSQLExpression;
import com.webobjects.eoaccess.EOSchemaGeneration;
import com.webobjects.eoaccess.EOSchemaSynchronization;
import com.webobjects.eoaccess.EOSynchronizationFactory;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSComparator;
import com.webobjects.foundation.NSComparator.ComparisonException;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSPropertyListSerialization;

import er.extensions.eof.ERXEntityFKConstraintOrder;
import er.extensions.foundation.ERXArrayUtilities;
import er.extensions.jdbc.ERXJDBCPlugInUtilities;



/**
 * <p>Sub-class of <code>com.webobjects.jdbcadaptor.MicrosoftPlugIn</code> to customize its functionality.
 * Use this by including this line in your connnection dictionary:</p>
 *
 * <pre>
 * plugin=er.extensions.jdbcadaptor.ERXMicrosoftPlugIn;
 * </pre>
 * @see com.webobjects.jdbcadaptor.MicrosoftPlugIn
 * @author chill
 */
@SuppressWarnings({ "unchecked", "rawtypes" , "deprecation"})
public class ERXMicrosoftPlugIn extends com.webobjects.jdbcadaptor.MicrosoftPlugIn {
	public static final Logger log = Logger.getLogger(ERXMicrosoftPlugIn.class);

	private static final String DriverClassName = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    private static final String QUERY_STRING_USE_BUNDLED_JDBC_INFO = "useBundledJdbcInfo";

    private static final String ALLOWS_NULL = "allowsNull";


    /**
     * @param adaptor the JDBCAdaptor that this adaptor configures
     */
    public ERXMicrosoftPlugIn(JDBCAdaptor adaptor) {
        super(adaptor);
    }

    @Override
    public String defaultDriverName() {
    	return DriverClassName;
    }
      
    
    /**
     * <P>This method returns true if the connection URL for the
     * database has a special flag on it which indicates to the
     * system that the jdbcInfo which has been bundled into the
     * plugin is acceptable to use in place of actually going to
     * the database and getting it.
     * @return <code>true</code> if the URL contains <code>useBundledJdbcInfo=</code> with true or yes
     */
    protected boolean shouldUseBundledJdbcInfo() {
        boolean shouldUseBundledJdbcInfo = true;
//        boolean shouldUseBundledJdbcInfo = false;
//      String url = connectionURL();
//      if (url != null) {
//          shouldUseBundledJdbcInfo = url.toLowerCase().matches(".*(;)" + QUERY_STRING_USE_BUNDLED_JDBC_INFO.toLowerCase() + "=(true|yes)(;.*|\\&.*|$)");
//      }
//
//    		if (log.isDebugEnabled()) log.debug("shouldUseBundledJdbcInfo: " + shouldUseBundledJdbcInfo);
      return shouldUseBundledJdbcInfo;
    }
    
    /**
     * <P>This is usually extracted from the the database using
     * JDBC, but this is really inconvenient for users who are
     * trying to generate SQL at some.  A specific version of the
     * data has been written into the property list of the
     * framework and this can be used as a hard-coded equivalent.
     * </P>
     */
	public NSDictionary jdbcInfo() {
    	// you can swap this code out to write the property list out in order
    	// to get a fresh copy of the JDBCInfo.plist.
    	//dumpJdbcInfo();

   		if (log.isDebugEnabled()) log.debug("called jdbcInfo");
    	
    	NSDictionary jdbcInfo;
    	// have a look at the JDBC connection URL to see if the flag has been set to
    	// specify that the hard-coded jdbcInfo information should be used.
    	if (shouldUseBundledJdbcInfo()) {
    		if(NSLog.debugLoggingAllowedForLevel(NSLog.DebugLevelDetailed)) {
    			NSLog.debug.appendln("Loading jdbcInfo from JDBCInfo.plist as opposed to using the JDBCPlugIn default implementation.");
    		}

    		InputStream jdbcInfoStream = NSBundle.bundleForClass(getClass()).inputStreamForResourcePath("JDBCInfo.plist");
    		if (jdbcInfoStream == null) {
    			throw new IllegalStateException("Unable to find 'JDBCInfo.plist' in this plugin jar.");
    		}

    		try {
    			jdbcInfo = (NSDictionary<String, Object>) NSPropertyListSerialization.propertyListFromData(new NSData(jdbcInfoStream, 2048), "US-ASCII");
    		} catch (IOException e) {
    			throw new RuntimeException("Failed to load 'JDBCInfo.plist' from this plugin jar.", e);
    		}
    	}
    	else {
    		jdbcInfo = super.jdbcInfo();
    	}
    	
   		if (log.isDebugEnabled()) log.debug("jdbcInfo: " + jdbcInfo);
    	return jdbcInfo;
    }

	protected void dumpJdbcInfo() {
		log.info("try to dump /tmp/JDBCInfo.plist...");
		try {
			String jdbcInfoS = NSPropertyListSerialization.stringFromPropertyList(super.jdbcInfo());
			FileOutputStream fos = new FileOutputStream("/tmp/JDBCInfo.plist");
			fos.write(jdbcInfoS.getBytes());
			fos.close();
		}
		catch(Exception e) {
			throw new IllegalStateException("problem writing JDBCInfo.plist",e);
		}
	}
	
/*
	@Override
	public EOSQLExpressionFactory createExpressionFactory() {
		return new ERXMicrosoftExpressionFactory(_adaptor);
	}
	
	public static class ERXMicrosoftExpressionFactory extends JDBCExpressionFactory {

		public ERXMicrosoftExpressionFactory(EOAdaptor adaptor) {
			super(adaptor);
		}
		

		@Override
		public EOSQLExpression createExpression(EOEntity entity) {
			EOSQLExpression expression =  super.createExpression(entity);
			//EOSQLExpression expression =  new ERXMicrosoftExpression(entity);
			return expression;
		}
		
		@Override
		public Class expressionClass() {
			return ERXMicrosoftExpression.class;
		}
		
	}
	
	
	public static class ERXMicrosoftExpression extends MicrosoftPlugIn.MicrosoftExpression {

		public ERXMicrosoftExpression(EOEntity entity) {
			super(entity);		
		}

		@Override
		public String columnTypeStringForAttribute(EOAttribute arg0) {
			return arg0.externalType().toLowerCase();
		//	return super.columnTypeStringForAttribute(arg0);
			
		}
		
	}
	*/
	
	public EOSynchronizationFactory createSynchronizationFactory() {
        NSLog.debug.appendln("Called createSynchronizationFactory");

        return new ERXMicrosoftSynchronizationFactory(_adaptor);
    }

    /**
     * Sub-class of <code>com.webobjects.jdbcadaptor.MicrosoftPlugIn.MicrosoftSynchronizationFactory</code> to customize
     * SQL generation.
     *
     * @see com.webobjects.jdbcadaptor.MicrosoftPlugIn.MicrosoftSynchronizationFactory
     * @see com.webobjects.eoaccess.EOSynchronizationFactory
     */
    public static class ERXMicrosoftSynchronizationFactory extends EOSynchronizationFactory {


    	/**
    	 * As hacks go, this one is pretty brutal.  In WO 5.4, the EOSynchronizationFactory was deprecated, and a
    	 * superclass, EOSchemaSynchronizationFactory, created.  EOSynchronizationFactory delegates many of its
    	 * former actions to an internal instance of EOSchemaSynchronizationFactory.  This mobius strip of mangled
    	 * logic makes customizing the behavior quite difficult, particularly if you are trying to maintain compatibility
    	 * with WO 5.3 an earlier versions.
    	 * <p>
    	 * The original problem behind this has was that as of 5.4, dropTableStatementsForEntityGroups no longer got
    	 * called (the version in EOSchemaSynchronizationFactory gets called, instead of the custom version in this class).
    	 * Rather than try and contort this code to get that called, the list of entities is sorted before any SQL
    	 * is generated.  Once 5.3 compatibility is no longer required, it should be possible to migrate this class
    	 * to the newer API and do a lot of cleanup.   In the meantime, there is this odious hack.
    	 * </p>
    	 *
    	 * @see com.webobjects.eoaccess.EOSynchronizationFactory#schemaCreationScriptForEntities(com.webobjects.foundation.NSArray, com.webobjects.foundation.NSDictionary)
    	 *
    	 * @param allEntities list of EOEntity to generate schema for
    	 * @param options schema generation options
    	 * @return SQL as a string
    	 */
    	public String schemaCreationScriptForEntities(NSArray allEntities, NSDictionary options) {
    		if (log.isDebugEnabled()) log.debug("allEntities: " + allEntities);

    		options = (options != null) ? options : NSDictionary.EmptyDictionary;

    		// Convert the list of entities into entity groups so that we can sort them with the existing code
    		NSArray groupedEntities = tableEntityGroupsForEntities(allEntities);
    		if (log.isDebugEnabled()) log.debug("groupedEntities: " + groupedEntities);

    		// Sort the groups entities
    		EOModelGroup modelGroup = ERXJDBCPlugInUtilities.modelGroupForEntityGroups(groupedEntities);
    		if (log.isDebugEnabled()) log.debug("modelGroup: " + modelGroup);

    		NSArray sortedEntities = NSArray.EmptyArray;

    		if(modelGroup != null) {
    			ERXEntityFKConstraintOrder constraintOrder = new ERXEntityFKConstraintOrder(modelGroup);
    			NSComparator entityOrderingComparator = new ERXJDBCPlugInUtilities.EntityGroupDeleteOrderComparator(constraintOrder);
    			try {
    				sortedEntities = groupedEntities.sortedArrayUsingComparator(entityOrderingComparator);
    			}
    			catch (ComparisonException e) {
    				throw NSForwardException._runtimeExceptionForThrowable(e);
    			}
    		}
    		else {
    			sortedEntities = groupedEntities;
    		}
    		if (log.isDebugEnabled()) log.debug("sortedEntities 1 : " + sortedEntities);

    		// Convert the grouped entities back into a normal list
    		sortedEntities = ERXArrayUtilities.flatten(sortedEntities);
    		if (log.isDebugEnabled()) log.debug("sortedEntities 2: " + sortedEntities);

    		StringBuffer result = new StringBuffer();
    		NSArray statements = schemaCreationStatementsForEntities(sortedEntities, options);
    		int i = 0;
    		for(int count = statements.count(); i < count; i++)
    			appendExpressionToScript((EOSQLExpression)statements.objectAtIndex(i), result);
    		return new String(result);
    	}


    	/**
    	 * Uses ERXEntityFKConstraintOrder to order the entities before generating the DROP TABLE statements as MSSQL does not
    	 * support deferred constraints.  Hence the tables need to be dropped so as to avoid foreign key constraint violations.
    	 *
    	 * This method is not called due to API changes in 5.4.
    	 *
    	 * @see com.webobjects.eoaccess.EOSynchronizationFactory#dropTableStatementsForEntityGroups(com.webobjects.foundation.NSArray)
    	 *
    	 * @param entityGroups array of arrays each containing one EOEntity
    	 * @return SQL to drop the tables for these entities in an order that avoids foreign key constraint violations
    	 */
    	public NSArray dropTableStatementsForEntityGroups(NSArray entityGroups) {

    		// SQL generation from Entity Modeler does not use the EOModelGroup.defaultGroup
    		ERXEntityFKConstraintOrder constraintOrder = new ERXEntityFKConstraintOrder(ERXJDBCPlugInUtilities.modelGroupForEntityGroups(entityGroups));
    		NSComparator entityOrderingComparator = new ERXJDBCPlugInUtilities.EntityGroupDeleteOrderComparator(constraintOrder);
    		try {
    			return super.dropTableStatementsForEntityGroups(entityGroups.sortedArrayUsingComparator(entityOrderingComparator));
    		}
    		catch (ComparisonException e) {
    			throw NSForwardException._runtimeExceptionForThrowable(e);
    		}
    	}

    	public NSArray primaryKeySupportStatementsForEntityGroups(NSArray entityGroups) {
    		String pkTable = ((JDBCAdaptor) adaptor()).plugIn().primaryKeyTableName();
    		NSMutableArray statements = new NSMutableArray();
    		statements.addObject(_expressionForString((new StringBuilder()).append("CREATE TABLE ").append(pkTable).append(" (NAME CHAR(40) PRIMARY KEY, PK INT)").toString()));
    		return statements;
    	}

    	public NSArray dropPrimaryKeySupportStatementsForEntityGroups(NSArray entityGroups) {
    		return new NSArray(_expressionForString((new StringBuilder()).append("DROP TABLE ").append(((JDBCAdaptor) adaptor()).plugIn().primaryKeyTableName()).toString()));
    	}

    	
    	public NSArray _statementsToDeleteTableNamedOptions(String tableName, EOSchemaGeneration options) {
    		return new NSArray(_expressionForString((new StringBuilder()).append("DROP TABLE ").append(tableName).toString()));
    	}

    	
    	public NSArray dropTableStatementsForEntityGroup(NSArray entityGroup) {
    		if (entityGroup == null) {
    			return NSArray.EmptyArray;
    		}

    		com.webobjects.eoaccess.EOSQLExpression sqlExpr = _expressionForString((new StringBuilder()).append("DROP TABLE ").append(((EOEntity) entityGroup.objectAtIndex(0)).externalName()).toString());
    		return new NSArray(sqlExpr);
    	}

    	public boolean supportsSchemaSynchronization() {
    		return false;
    	}

    	public NSArray _statementsToDropPrimaryKeyConstraintsOnTableNamed(String tableName) {
    		return NSArray.EmptyArray;
    	}

    	public NSArray statementsToRenameTableNamed(String tableName, String newName, EOSchemaGeneration options) {
    		String aTableName = tableName;
    		int lastDot = aTableName.lastIndexOf('.');
    		if (lastDot != -1) {
    			aTableName = aTableName.substring(lastDot + 1);
    		}
    		String aNewName = newName;
    		lastDot = aNewName.lastIndexOf('.');
    		if (lastDot != -1) {
    			aNewName = aNewName.substring(lastDot + 1);
    		}
    		return new NSArray(_expressionForString((new StringBuilder()).append("execute sp_rename ").append(aTableName).append(", ").append(aNewName).toString()));
    	}

    	/**
    	 * @see com.webobjects.eoaccess.EOSynchronizationFactory#statementsToRenameColumnNamed(java.lang.String, java.lang.String, java.lang.String, com.webobjects.foundation.NSDictionary)
    	 */
    	public NSArray<EOSQLExpression> statementsToRenameColumnNamed(String columnName, String tableName,
    			String newName,
    			NSDictionary<String, String> options)
    			{
    		String lameMSRename = new StringBuilder().append("EXEC sp_rename '").append(tableName).append(".").append(columnName).
    		append("', '").append(newName).append("', 'COLUMN';").toString();
    		return new NSArray(_expressionForString(lameMSRename));
    			}


    	public NSArray statementsToConvertColumnType(String columnName, String tableName, ColumnTypes oldType, ColumnTypes newType, NSDictionary options) {
    		String columnTypeString = statementToCreateDataTypeClause(newType);
    		Boolean allowsNull = (Boolean) options.valueForKey(ALLOWS_NULL);
    		StringBuffer sb = new StringBuffer();
    		sb.append("alter table ").append(quoteNames(tableName));
    		sb.append(" alter column ").append(quoteNames(columnName));
    		sb.append(' ').append(columnTypeString);
    		if (allowsNull != null) {
    			sb.append(' ');
    			sb.append(allowsNull.booleanValue() ? "" : " NOT NULL");
    		}

    		NSArray statements = new NSArray(_expressionForString(sb.toString()));
    		return statements;
    	}

    	public NSArray statementsToModifyColumnNullRule(String columnName, String tableName, boolean allowsNull, NSDictionary nsdictionary) {
    		/*
    		 * SQL Server allows names on constraints, but seems to discard them so we can't use named constraint dropping such as is
    		 * done in other database.  To use ALTER TABLE ... ALTER COLUMN, SQL Server requires the column type to be specified.  As
    		 * that is not passed to this method, using statementsToConvertColumnType is the only practical solution.
    		 */
    		throw new RuntimeException("statementsToModifyColumnNullRule don't work for SQL Server, try changing the column type with an options dictionary, e.g." +
    		"new NSDictionary(Boolean.TRUE, \"allowsNull\")");
    	}

    	/**
    	 * Hackalert: Für newIntBooleanColumn wird kein Default wert generiert
    	 * Ob generell kein Defaultwert generiert wird, weiss ich nicht :-(
    	 * @param attribute
    	 * @return 
    	 */
    	private String defaultClause(EOAttribute attribute) {
    		String returnValue = null;
    		
    		NSDictionary userInfo = (NSDictionary) attribute.userInfo();
    		if(userInfo != null && userInfo.containsKey("default")) {
    			Object defaultValue = userInfo.objectForKey("default");
    			if(attribute.externalType().equals("int") && defaultValue instanceof Boolean) {
    				returnValue = "DEFAULT " + (((Boolean)defaultValue)?"1":"0");
    			}
    		}
    		return returnValue;
    	}
    	
    	/**
    	 * @see com.webobjects.eoaccess.EOSynchronizationFactory#statementsToInsertColumnForAttribute(com.webobjects.eoaccess.EOAttribute, com.webobjects.foundation.NSDictionary)
    	 */
    	public NSArray statementsToInsertColumnForAttribute(EOAttribute attribute, NSDictionary options) {
    		String clause = _columnCreationClauseForAttribute(attribute);

    		String defaultClause = defaultClause(attribute);
    		if(log.isDebugEnabled()) log.debug("statementsToInsertColumnForAttribute: " + defaultClause);
    		
    		if(defaultClause != null) {
    			clause += " " + defaultClause;
    		}
    	
    		return new NSArray(_expressionForString("alter table " + quoteNames(attribute.entity().externalName()) + " add " + clause));
    	}


    	private String statementToCreateDataTypeClause(EOSchemaSynchronization.ColumnTypes columntypes) {
    		int size = columntypes.precision();
    		if (size == 0) {
    			size = columntypes.width();
    		}

    		if (size == 0) {
    			return columntypes.name();
    		}

    		int scale = columntypes.scale();
    		if (scale == 0) {
    			return columntypes.name() + "(" + size + ")";
    		}

    		return columntypes.name() + "(" + size + "," + scale + ")";
    	}


    	protected static String quoteNames(String s) {
    		if (s == null)
    			return null;
    		int i = s.lastIndexOf(46);
    		if (i == -1)
    			return "\"" + s + "\"";
    		else
    			return "\"" + s.substring(0, i) + "\".\"" + s.substring(i + 1, s.length()) + "\"";
    	}

    	public ERXMicrosoftSynchronizationFactory(EOAdaptor adaptor) {
    		super(adaptor);
    		NSLog.debug.appendln("Created ERXMicrosoftSynchronizationFactory");
    	}

    } // End ERXMicrosoftSynchronizationFactory

    
}
