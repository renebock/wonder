package er.microsoftplugin;

import com.webobjects.jdbcadaptor.ERXMicrosoftPlugIn;
import com.webobjects.jdbcadaptor.JDBCPlugIn;

import er.extensions.ERXExtensions;
import er.extensions.ERXFrameworkPrincipal;

public class MicrosoftPlugIn extends ERXFrameworkPrincipal {

	public final static Class<?> REQUIRES[] = new Class[] {ERXExtensions.class};

	static {
		setUpFrameworkPrincipalClass(MicrosoftPlugIn.class);
		JDBCPlugIn.setPlugInNameForSubprotocol(ERXMicrosoftPlugIn.class.getName(), "sqlserver");
	}

	@Override
	public void finishInitialization() {
		
	}

}
