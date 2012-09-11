/*******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2012 by Pentaho : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.di.trans.steps.monetdbbulkloader;

import java.util.List;
import java.util.Map;

import org.pentaho.di.core.CheckResult;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.Counter;
import org.pentaho.di.core.SQLStatement;
import org.pentaho.di.core.database.Database;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.database.MonetDBDatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.shared.SharedObjectInterface;
import org.pentaho.di.trans.DatabaseImpact;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.di.core.ProvidesDatabaseConnectionInformation;
import org.w3c.dom.Node;


/**
 * Created on 20-feb-2007
 * 
 * @author Sven Boden
 */
public class MonetDBBulkLoaderMeta extends BaseStepMeta implements StepMetaInterface, ProvidesDatabaseConnectionInformation
{
	private static Class<?> PKG = MonetDBBulkLoaderMeta.class; // for i18n purposes, needed by Translator2!!   $NON-NLS-1$

	/**  The database connection name **/
    private String dbConnectionName;
	
	/** what's the schema for the target? */
    private String schemaName;

    /** what's the table for the target? */
	private String tableName;
	
	/** Path to the mclient utility */
	private String mClientPath;
	
	/** Path to the log file */
	private String logFile;

    /** database connection */
	private DatabaseMeta databaseMeta;

    /** Field name of the target table */
	private String fieldTable[];

    /** Field name in the stream */
	private String fieldStream[];

	/** flag to indicate that the format is OK for MonetDB*/
	private boolean fieldFormatOk[];

	/** Encoding to use */
	private String encoding;
	
	/** Truncate table? */
	private boolean truncate = false;
	
	/** Auto adjust the table structure? */
	private boolean autoSchema = false;
		
	/** Auto adjust strings that are too long? */
	private boolean autoStringWidths = false;

	public boolean isAutoStringWidths() {
		return autoStringWidths;
	}

	public void setAutoStringWidths(boolean autoStringWidths) {
		this.autoStringWidths = autoStringWidths;
	}

	public boolean isTruncate() {
		return truncate;
	}

	public void setTruncate(boolean truncate) {
		this.truncate = truncate;
	}

	public boolean isAutoSchema() {
		return autoSchema;
	}

	public void setAutoSchema(boolean autoSchema) {
		this.autoSchema = autoSchema;
	}

	/** The number of rows to buffer before passing them over to MonetDB.
	 *  This number should be non-zero since we need to specify the number of rows we pass.
	 */
	private String bufferSize;
	
		
	public MonetDBBulkLoaderMeta()
	{
		super();
	}

    /**
     * @return Returns the database.
     */
    public DatabaseMeta getDatabaseMeta()
    {
        return databaseMeta;
    }

    /**
     * @return Returns the database.
     */
    public DatabaseMeta getDatabaseMeta(MonetDBBulkLoader loader)
    {
        return databaseMeta;
    }

    /**
     * @param database The database to set.
     */
    public void setDatabaseMeta(DatabaseMeta database)
    {
        this.databaseMeta = database;
    }

    /**
     * @return Returns the tableName.
     */
    public String getTableName()
    {
        return tableName;
    }

    /**
     * @param tableName The tableName to set.
     */
    public void setTableName(String tableName)
    {
        this.tableName = tableName;
    }
    
    /**
     * @return Returns the fieldTable.
     */
    public String[] getFieldTable()
    {
        return fieldTable;
    }

    /**
     * @param fieldTable The fieldTable to set.
     */
    public void setFieldTable(String[] fieldTable)
    {
        this.fieldTable = fieldTable;
    }

    /**
     * @return Returns the fieldStream.
     */
    public String[] getFieldStream()
    {
        return fieldStream;
    }

    /**
     * @param fieldStream The fieldStream to set.
     */
    public void setFieldStream(String[] fieldStream)
    {
        this.fieldStream = fieldStream;
    }

	public void loadXML(Node stepnode, List<DatabaseMeta> databases, Map<String, Counter> counters)
		throws KettleXMLException
	{
		readData(stepnode, databases);
	}

	public void allocate(int nrvalues)
	{
		fieldTable  = new String[nrvalues];
		fieldStream = new String[nrvalues];
		fieldFormatOk = new boolean[nrvalues];
	}

	public Object clone()
	{
		MonetDBBulkLoaderMeta retval = (MonetDBBulkLoaderMeta)super.clone();
		int nrvalues  = fieldTable.length;

		retval.allocate(nrvalues);

		for (int i=0;i<nrvalues;i++)
		{
			retval.fieldTable[i]  = fieldTable[i];
			retval.fieldStream[i] = fieldStream[i];
		}
		return retval;
	}

	private void readData(Node stepnode, List<? extends SharedObjectInterface> databases)
		throws KettleXMLException
	{
		try
		{
			dbConnectionName     = XMLHandler.getTagValue(stepnode, "connection");   //$NON-NLS-1$
			databaseMeta   = DatabaseMeta.findDatabase(databases, dbConnectionName);

			bufferSize = XMLHandler.getTagValue(stepnode, "buffer_size");       //$NON-NLS-1$

			schemaName     = XMLHandler.getTagValue(stepnode, "schema");       //$NON-NLS-1$
			tableName      = XMLHandler.getTagValue(stepnode, "table");        //$NON-NLS-1$
			
			mClientPath         = XMLHandler.getTagValue(stepnode, "mclient_path");       //$NON-NLS-1$
			logFile        = XMLHandler.getTagValue(stepnode, "log_file");     //$NON-NLS-1$
			encoding       = XMLHandler.getTagValue(stepnode, "encoding");         //$NON-NLS-1$
			truncate       = "Y".equals(XMLHandler.getTagValue(stepnode, "truncate"));         //$NON-NLS-1$
			autoSchema     = "Y".equals(XMLHandler.getTagValue(stepnode, "auto_schema"));         //$NON-NLS-1$
			autoStringWidths = "Y".equals(XMLHandler.getTagValue(stepnode, "auto_string_widths"));         //$NON-NLS-1$
			

			int nrvalues = XMLHandler.countNodes(stepnode, "mapping");      //$NON-NLS-1$
			allocate(nrvalues);

			for (int i=0;i<nrvalues;i++)
			{
				Node vnode = XMLHandler.getSubNodeByNr(stepnode, "mapping", i);    //$NON-NLS-1$

				fieldTable[i]      = XMLHandler.getTagValue(vnode, "stream_name"); //$NON-NLS-1$
				fieldStream[i]     = XMLHandler.getTagValue(vnode, "field_name");  //$NON-NLS-1$
				if (fieldStream[i]==null) fieldStream[i]=fieldTable[i];            // default: the same name!
				fieldFormatOk[i]  = "Y".equalsIgnoreCase(XMLHandler.getTagValue(vnode, "field_format_ok"));  //$NON-NLS-1$
			}
		}
		catch(Exception e)
		{
			throw new KettleXMLException(BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.Exception.UnableToReadStepInfoFromXML"), e); //$NON-NLS-1$
		}
	}

	public void setDefault()
	{
		fieldTable   = null;
		databaseMeta = null;
		bufferSize   = "100000";
        schemaName   = "";                //$NON-NLS-1$
		tableName    = BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.DefaultTableName"); //$NON-NLS-1$
		mClientPath  = "/usr/local/bin/mclient";              //$NON-NLS-1$
		logFile      = "";                                    //$NON-NLS-1$
        encoding     = "";                                    //$NON-NLS-1$
        truncate 	 = false;
        autoSchema   = false;
        autoStringWidths = false;
        
		allocate(0);
	}

	public String getXML()
	{
        StringBuffer retval = new StringBuffer(300);

		retval.append("    ").append(XMLHandler.addTagValue("connection",   dbConnectionName)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		retval.append("    ").append(XMLHandler.addTagValue("buffer_size",  bufferSize));     //$NON-NLS-1$ //$NON-NLS-2$
        retval.append("    ").append(XMLHandler.addTagValue("schema",       schemaName));    //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("table",        tableName));     //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("mclient_path", mClientPath));        //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("log_file",     logFile));       //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("encoding",     encoding));      //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("truncate",     truncate));      //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("auto_schema",     autoSchema));      //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("auto_string_widths",     autoStringWidths));      //$NON-NLS-1$ //$NON-NLS-2$
		
		for (int i=0;i<fieldTable.length;i++)
		{
			retval.append("      <mapping>").append(Const.CR); //$NON-NLS-1$
			retval.append("        ").append(XMLHandler.addTagValue("stream_name", fieldTable[i])); //$NON-NLS-1$ //$NON-NLS-2$
			retval.append("        ").append(XMLHandler.addTagValue("field_name",  fieldStream[i])); //$NON-NLS-1$ //$NON-NLS-2$
			retval.append("        ").append(XMLHandler.addTagValue("field_format_ok",  fieldFormatOk[i])); //$NON-NLS-1$ //$NON-NLS-2$
			retval.append("      </mapping>").append(Const.CR); //$NON-NLS-1$
		}

		return retval.toString();
	}

	public void readRep(Repository rep, ObjectId id_step, List<DatabaseMeta> databases, Map<String, Counter> counters)
		throws KettleException
	{
		try
		{
			databaseMeta = rep.loadDatabaseMetaFromStepAttribute(id_step, "id_connection", databases);
     		bufferSize     =      rep.getStepAttributeString(id_step, "buffer_size");         //$NON-NLS-1$
     		dbConnectionName =      rep.getStepAttributeString(id_step,  "db_connection_name");         //$NON-NLS-1$
            schemaName     =      rep.getStepAttributeString(id_step,  "schema");         //$NON-NLS-1$
			tableName      =      rep.getStepAttributeString(id_step,  "table");          //$NON-NLS-1$
			mClientPath    =      rep.getStepAttributeString(id_step,  "mclient_path");         //$NON-NLS-1$
			logFile        =      rep.getStepAttributeString(id_step,  "log_file");       //$NON-NLS-1$
			encoding       =      rep.getStepAttributeString(id_step,  "encoding");       //$NON-NLS-1$
			truncate       =      Boolean.parseBoolean(rep.getStepAttributeString(id_step,  "truncate"));       //$NON-NLS-1$
			autoSchema     =      Boolean.parseBoolean(rep.getStepAttributeString(id_step,  "auto_schema"));       //$NON-NLS-1$
			autoStringWidths =    Boolean.parseBoolean(rep.getStepAttributeString(id_step,  "auto_string_widths"));       //$NON-NLS-1$
			
			int nrvalues = rep.countNrStepAttributes(id_step, "stream_name");             //$NON-NLS-1$

			allocate(nrvalues);

			for (int i=0;i<nrvalues;i++)
			{
				fieldTable[i]  = rep.getStepAttributeString(id_step, i, "stream_name");   //$NON-NLS-1$
				fieldStream[i] = rep.getStepAttributeString(id_step, i, "field_name");    //$NON-NLS-1$
				if (fieldStream[i]==null) fieldStream[i]=fieldTable[i];        
				fieldFormatOk[i] = rep.getStepAttributeBoolean(id_step, i, "field_format_ok");    //$NON-NLS-1$
			}
		}
		catch(Exception e)
		{
			throw new KettleException(BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.Exception.UnexpectedErrorReadingStepInfoFromRepository"), e); //$NON-NLS-1$
		}
	}

	public void saveRep(Repository rep, ObjectId id_transformation, ObjectId id_step)
		throws KettleException
	{
		try
		{
			rep.saveDatabaseMetaStepAttribute(id_transformation, id_step, "id_connection", databaseMeta);
			rep.saveStepAttribute(id_transformation, id_step, "buffer_size",     bufferSize);     //$NON-NLS-1$
			 rep.saveStepAttribute(id_transformation, id_step, "db_connection_name",          dbConnectionName);    //$NON-NLS-1$
            rep.saveStepAttribute(id_transformation, id_step, "schema",          schemaName);    //$NON-NLS-1$
			rep.saveStepAttribute(id_transformation, id_step, "table",           tableName);     //$NON-NLS-1$
			
			rep.saveStepAttribute(id_transformation, id_step, "mclient_path",    mClientPath);        //$NON-NLS-1$
			rep.saveStepAttribute(id_transformation, id_step, "log_file",        logFile);       //$NON-NLS-1$

			rep.saveStepAttribute(id_transformation, id_step, "encoding",        encoding);      //$NON-NLS-1$
			rep.saveStepAttribute(id_transformation, id_step, "truncate",        truncate);      //$NON-NLS-1$
			rep.saveStepAttribute(id_transformation, id_step, "auto_schema",     autoSchema);      //$NON-NLS-1$
			rep.saveStepAttribute(id_transformation, id_step, "auto_string_widths",     autoStringWidths);      //$NON-NLS-1$

			for (int i=0;i<fieldTable.length;i++)
			{
				rep.saveStepAttribute(id_transformation, id_step, i, "stream_name", fieldTable[i]);  //$NON-NLS-1$
				rep.saveStepAttribute(id_transformation, id_step, i, "field_name",  fieldStream[i]); //$NON-NLS-1$
				rep.saveStepAttribute(id_transformation, id_step, i, "field_format_ok",  fieldFormatOk[i]); //$NON-NLS-1$
			}

			// Also, save the step-database relationship!
			if (databaseMeta!=null) rep.insertStepDatabase(id_transformation, id_step, databaseMeta.getObjectId());
		}
		catch(Exception e)
		{
			throw new KettleException(BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.Exception.UnableToSaveStepInfoToRepository")+id_step, e); //$NON-NLS-1$
		}
	}
	
	public void getFields(RowMetaInterface rowMeta, String origin, RowMetaInterface[] info, StepMeta nextStep, VariableSpace space) throws KettleStepException
	{
		// Default: nothing changes to rowMeta
	}

	public void check(List<CheckResultInterface> remarks, TransMeta transMeta, StepMeta stepMeta, RowMetaInterface prev, String input[], String output[], RowMetaInterface info)
	{
		CheckResult cr;
		String error_message = ""; //$NON-NLS-1$

		if (databaseMeta!=null)
		{
			Database db = new Database(loggingObject, databaseMeta);
			db.shareVariablesWith(transMeta);
			try
			{
				db.connect();

				if (!Const.isEmpty(tableName))
				{
					cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.TableNameOK"), stepMeta); //$NON-NLS-1$
					remarks.add(cr);

					boolean first=true;
					boolean error_found=false;
					error_message = ""; //$NON-NLS-1$
					
					// Check fields in table
                    String schemaTable = databaseMeta.getQuotedSchemaTableCombination(
                    		                   transMeta.environmentSubstitute(schemaName), 
                    		                   transMeta.environmentSubstitute(tableName));
					RowMetaInterface r = db.getTableFields(schemaTable);
					if (r!=null)
					{
						cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.TableExists"), stepMeta); //$NON-NLS-1$
						remarks.add(cr);

						// How about the fields to insert/dateMask in the table?
						first=true;
						error_found=false;
						error_message = ""; //$NON-NLS-1$
						
						for (int i=0;i<fieldTable.length;i++)
						{
							String field = fieldTable[i];

							ValueMetaInterface v = r.searchValueMeta(field);
							if (v==null)
							{
								if (first)
								{
									first=false;
									error_message+=BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.MissingFieldsToLoadInTargetTable")+Const.CR; //$NON-NLS-1$
								}
								error_found=true;
								error_message+="\t\t"+field+Const.CR;  //$NON-NLS-1$
							}
						}
						if (error_found)
						{
							cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta);
						}
						else
						{
							cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.AllFieldsFoundInTargetTable"), stepMeta); //$NON-NLS-1$
						}
						remarks.add(cr);
					}
					else
					{
						error_message=BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.CouldNotReadTableInfo"); //$NON-NLS-1$
						cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta);
						remarks.add(cr);
					}
				}

				// Look up fields in the input stream <prev>
				if (prev!=null && prev.size()>0)
				{
					cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.StepReceivingDatas",prev.size()+""), stepMeta); //$NON-NLS-1$ //$NON-NLS-2$
					remarks.add(cr);

					boolean first=true;
					error_message = ""; //$NON-NLS-1$
					boolean error_found = false;

					for (int i=0;i<fieldStream.length;i++)
					{
						ValueMetaInterface v = prev.searchValueMeta(fieldStream[i]);
						if (v==null)
						{
							if (first)
							{
								first=false;
								error_message+=BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.MissingFieldsInInput")+Const.CR; //$NON-NLS-1$
							}
							error_found=true;
							error_message+="\t\t"+fieldStream[i]+Const.CR;  //$NON-NLS-1$
						}
					}
					if (error_found)
 					{
						cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta);
					}
					else
					{
						cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.AllFieldsFoundInInput"), stepMeta); //$NON-NLS-1$
					}
					remarks.add(cr);
				}
				else
				{
					error_message=BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.MissingFieldsInInput3")+Const.CR; //$NON-NLS-1$
					cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta);
					remarks.add(cr);
				}
			}
			catch(KettleException e)
			{
				error_message = BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.DatabaseErrorOccurred")+e.getMessage(); //$NON-NLS-1$
				cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta);
				remarks.add(cr);
			}
			finally
			{
				db.disconnect();
			}
		}
		else
		{
			error_message = BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.InvalidConnection"); //$NON-NLS-1$
			cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta);
			remarks.add(cr);
		}

		// See if we have input streams leading to this step!
		if (input.length>0)
		{
			cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.StepReceivingInfoFromOtherSteps"), stepMeta); //$NON-NLS-1$
			remarks.add(cr);
		}
		else
		{
			cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.CheckResult.NoInputError"), stepMeta); //$NON-NLS-1$
			remarks.add(cr);
		}
	}

	public SQLStatement getTableDdl( TransMeta transMeta, String stepname, boolean autoSchema, MonetDBBulkLoaderData data, boolean safeMode ) throws KettleException
	{

			String name = stepname; // new name might not yet be linked to other steps!
			StepMeta stepMeta = new StepMeta(BaseMessages.getString(PKG, "MonetDBBulkLoaderDialog.StepMeta.Title"), name, this); //$NON-NLS-1$
			RowMetaInterface prev = transMeta.getPrevStepFields(stepname);

			SQLStatement sql = getSQLStatements(transMeta, stepMeta, prev, autoSchema, data, safeMode);
			return sql;
	}	

	public SQLStatement getSQLStatements(TransMeta transMeta, StepMeta stepMeta, RowMetaInterface prev, boolean autoSchema, MonetDBBulkLoaderData data, boolean safeMode) throws KettleStepException
	{
		SQLStatement retval = new SQLStatement(stepMeta.getName(), databaseMeta, null); // default: nothing to do!

		if (databaseMeta!=null)
		{
			if (prev!=null && prev.size()>0)
			{
                // Copy the row
                RowMetaInterface tableFields = new RowMeta();

                if( autoSchema ) {
                	// update the field table from the fields coming from the previous step
                	List<ValueMetaInterface> fields = prev.getValueMetaList();
                	fieldTable = new String[fields.size()];
                	fieldStream = new String[fields.size()];
                	fieldFormatOk = new boolean[fields.size()];
                	int idx = 0;
                	for( ValueMetaInterface field: fields) {
                        ValueMetaInterface tableField = field.clone();
                        tableFields.addValueMeta(tableField);
                        fieldTable[idx] = field.getName();
                        fieldStream[idx] = field.getName();
                        fieldFormatOk[idx] = true;
                	}
                	
    				data.keynrs = new int[getFieldStream().length];
    				for (int i=0;i<data.keynrs.length;i++) {
    					data.keynrs[i] = i;
    				}                } else {
	                // Now change the field names
	                for (int i=0;i<fieldTable.length;i++)
	                {
	                    ValueMetaInterface v = prev.searchValueMeta(fieldStream[i]);
	                    if (v!=null)
	                    {
	                        ValueMetaInterface tableField = v.clone();
	                        tableField.setName(fieldTable[i]);
	                        tableFields.addValueMeta(tableField);
	                    }
	                    else
	                    {
	                        throw new KettleStepException("Unable to find field ["+fieldStream[i]+"] in the input rows");
	                    }
	                }
                }
	                
				if (!Const.isEmpty(tableName))
				{
					Database db = new Database(loggingObject, databaseMeta);
                    db.shareVariablesWith(transMeta);
					try
					{
						db.connect();

                        String schemaTable = databaseMeta.getQuotedSchemaTableCombination(transMeta.environmentSubstitute(schemaName), 
                        		                                                          transMeta.environmentSubstitute(tableName));                        
                        MonetDBDatabaseMeta.safeModeLocal.set(safeMode);
                        String cr_table = db.getDDL(schemaTable,
													tableFields,
													null,
													false,
													null,
													true
													);

						String sql = cr_table;
						if (sql.length()==0) retval.setSQL(null); else retval.setSQL(sql);
					}
					catch(KettleException e)
					{
						retval.setError(BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.GetSQL.ErrorOccurred")+e.getMessage()); //$NON-NLS-1$
					}
					finally {
						db.disconnect();
						MonetDBDatabaseMeta.safeModeLocal.remove();
					}
				}
				else
				{
					retval.setError(BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.GetSQL.NoTableDefinedOnConnection")); //$NON-NLS-1$
				}
			}
			else
			{
				retval.setError(BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.GetSQL.NotReceivingAnyFields")); //$NON-NLS-1$
			}
		}
		else
		{
			retval.setError(BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.GetSQL.NoConnectionDefined")); //$NON-NLS-1$
		}

		return retval;
	}

	public void analyseImpact(List<DatabaseImpact> impact, TransMeta transMeta, StepMeta stepMeta, RowMetaInterface prev, String input[], String output[], RowMetaInterface info) throws KettleStepException
    {
        if (prev != null)
        {
            /* DEBUG CHECK THIS */
            // Insert dateMask fields : read/write
            for (int i = 0; i < fieldTable.length; i++)
            {
                ValueMetaInterface v = prev.searchValueMeta(fieldStream[i]);

                DatabaseImpact ii = new DatabaseImpact(DatabaseImpact.TYPE_IMPACT_READ_WRITE, transMeta.getName(), stepMeta.getName(), databaseMeta
                        .getDatabaseName(), transMeta.environmentSubstitute(tableName), fieldTable[i], fieldStream[i], v!=null?v.getOrigin():"?", "", "Type = " + v.toStringMeta()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                impact.add(ii);
            }
        }
	}

	public StepInterface getStep(StepMeta stepMeta, StepDataInterface stepDataInterface, int cnr, TransMeta transMeta, Trans trans)
	{
		return new MonetDBBulkLoader(stepMeta, stepDataInterface, cnr, transMeta, trans);
	}

	public StepDataInterface getStepData()
	{
		return new MonetDBBulkLoaderData();
	}

    public DatabaseMeta[] getUsedDatabaseConnections()
    {
        if (databaseMeta!=null)
        {
            return new DatabaseMeta[] { databaseMeta };
        }
        else
        {
            return super.getUsedDatabaseConnections();
        }
    }

    public RowMetaInterface getRequiredFields(VariableSpace space) throws KettleException
    {
    	String realTableName = space.environmentSubstitute(tableName);
    	String realSchemaName = space.environmentSubstitute(schemaName);

        if (databaseMeta!=null)
        {
            Database db = new Database(loggingObject, databaseMeta);
            try
            {
                db.connect();

                if (!Const.isEmpty(realTableName))
                {
                    String schemaTable = databaseMeta.getQuotedSchemaTableCombination(realSchemaName, realTableName);

                    // Check if this table exists...
                    if (db.checkTableExists(schemaTable))
                    {
                        return db.getTableFields(schemaTable);
                    }
                    else
                    {
                        throw new KettleException(BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.Exception.TableNotFound"));
                    }
                }
                else
                {
                    throw new KettleException(BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.Exception.TableNotSpecified"));
                }
            }
            catch(Exception e)
            {
                throw new KettleException(BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.Exception.ErrorGettingFields"), e);
            }
            finally
            {
                db.disconnect();
            }
        }
        else
        {
            throw new KettleException(BaseMessages.getString(PKG, "MonetDBBulkLoaderMeta.Exception.ConnectionNotDefined"));
        }

    }

    /**
     * @return the schemaName
     */
    public String getSchemaName()
    {
        return schemaName;
    }

    /**
     * @param schemaName the schemaName to set
     */
    public void setSchemaName(String schemaName)
    {
        this.schemaName = schemaName;
    }

    public String getLogFile() {
		return logFile;
	}

	public void setLogFile(String logFile) {
		this.logFile = logFile;
	}
	
	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public String getDelimiter() {
		return ",";
	}

	public String getEnclosure() {
		return "\"";
	}

	/**
	 * @return the bufferSize
	 */
	public String getBufferSize() {
		return bufferSize;
	}

	/**
	 * @param bufferSize the bufferSize to set
	 */
	public void setBufferSize(String bufferSize) {
		this.bufferSize = bufferSize;
	}

	/**
	 * @return the fieldFormatOk
	 */
	public boolean[] getFieldFormatOk() {
		return fieldFormatOk;
	}

	/**
	 * @param fieldFormatOk the fieldFormatOk to set
	 */
	public void setFieldFormatOk(boolean[] fieldFormatOk) {
		this.fieldFormatOk = fieldFormatOk;
	}

	/**
	 * @param clientPath the mClientPath to set
	 */
	public void setMClientPath(String clientPath) {
		mClientPath = clientPath;
	}

	/**
	 * @return the mClientPath
	 */
	public String getMClientPath() {
		return mClientPath;
	}

	@Override
	public String getMissingDatabaseConnectionInformationMessage() {
		// TODO
		return null;
	}
	
	/**
	 * @param database connection name to set
	 */
	public void setDbConnectionName(String dbConnectionName) {
		this.dbConnectionName = dbConnectionName;
	}
	
	/**
	 * @return the database connection name
	 */
	public String getDbConnectionName() {
		return this.dbConnectionName;
	}

}