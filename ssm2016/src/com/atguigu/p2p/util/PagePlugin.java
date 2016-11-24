package com.atguigu.p2p.util;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.statement.BaseStatementHandler;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.scripting.xmltags.ForEachSqlNode;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

@Intercepts({@Signature(type=StatementHandler.class,method="prepare",args={Connection.class})})
public class PagePlugin implements Interceptor 
{

	private static String dialect = "";	//鏁版嵁搴撴柟瑷�
	private static String pageSqlId = ""; //mapper.xml涓渶瑕佹嫤鎴殑ID(姝ｅ垯鍖归厤)
	
	public Object intercept(Invocation ivk) throws Throwable 
	{
		if(ivk.getTarget() instanceof RoutingStatementHandler)
		{
			RoutingStatementHandler statementHandler = (RoutingStatementHandler)ivk.getTarget();
			BaseStatementHandler delegate = (BaseStatementHandler) ReflectHelper.getValueByFieldName(statementHandler, "delegate");
			MappedStatement mappedStatement = (MappedStatement) ReflectHelper.getValueByFieldName(delegate, "mappedStatement");

			if(mappedStatement.getId().matches(pageSqlId))
			{ 	
				//鎷︽埅闇�鍒嗛〉鐨凷QL
				BoundSql boundSql = delegate.getBoundSql();
				//鍒嗛〉SQL<select>涓璸arameterType灞炴�瀵瑰簲鐨勫疄浣撳弬鏁帮紝鍗矼apper鎺ュ彛涓墽琛屽垎椤垫柟娉曠殑鍙傛暟,璇ュ弬鏁颁笉寰椾负绌�
				Object parameterObject = boundSql.getParameterObject();
				
				if(parameterObject==null)
				{
					throw new NullPointerException("parameterObject灏氭湭瀹炰緥鍖栵紒");
				}
				else
				{
					Connection connection = (Connection)ivk.getArgs()[0];
					String sql = boundSql.getSql();
					String countSql = "select count(0) from (" + sql+ ") as tmp_count"; //璁板綍缁熻
					PreparedStatement countStmt = connection.prepareStatement(countSql);
					BoundSql countBS = new BoundSql(mappedStatement.getConfiguration(),countSql,boundSql.getParameterMappings(),parameterObject);
					setParameters(countStmt,mappedStatement,countBS,parameterObject);
					ResultSet rs = countStmt.executeQuery();
					int count = 0;
					if (rs.next()) 
					{
						count = rs.getInt(1);
					}
					rs.close();
					countStmt.close();

					Page page = null;
					if(parameterObject instanceof Page)
					{	//鍙傛暟灏辨槸Page瀹炰綋
						 page = (Page) parameterObject;
						 page.setTotalCount(count);
					}
					else
					{	//鍙傛暟涓烘煇涓疄浣擄紝璇ュ疄浣撴嫢鏈塒age灞炴�
						Field pageField = ReflectHelper.getFieldByFieldName(parameterObject,"page");
						if(pageField != null)
						{
							page = (Page) ReflectHelper.getValueByFieldName(parameterObject,"page");
							
							if(page == null)
							{
								page = new Page();
							}
							
							page.setTotalCount(count);
							ReflectHelper.setValueByFieldName(parameterObject,"page", page); //閫氳繃鍙嶅皠锛屽瀹炰綋瀵硅薄璁剧疆鍒嗛〉瀵硅薄
						}
						else
						{
							throw new NoSuchFieldException(parameterObject.getClass().getName()+"涓嶅瓨鍦�page 灞炴�锛�");
						}
					}
					String pageSql = generatePageSql(sql,page);
					ReflectHelper.setValueByFieldName(boundSql, "sql", pageSql); //灏嗗垎椤祍ql璇彞鍙嶅皠鍥濨oundSql.
				}
			}
		}
		return ivk.proceed();
	}

	
	
	/**
	 * 瀵筍QL鍙傛暟(?)璁惧�,鍙傝�org.apache.ibatis.executor.parameter.DefaultParameterHandler
	 * @param ps
	 * @param mappedStatement
	 * @param boundSql
	 * @param parameterObject
	 * @throws SQLException
	 */
	private void setParameters(PreparedStatement ps,MappedStatement mappedStatement,BoundSql boundSql,Object parameterObject) throws SQLException {
		ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());
		List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
		if (parameterMappings != null) {
			Configuration configuration = mappedStatement.getConfiguration();
			TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
			MetaObject metaObject = parameterObject == null ? null: configuration.newMetaObject(parameterObject);
			for (int i = 0; i < parameterMappings.size(); i++) {
				ParameterMapping parameterMapping = parameterMappings.get(i);
				if (parameterMapping.getMode() != ParameterMode.OUT) {
					Object value;
					String propertyName = parameterMapping.getProperty();
					PropertyTokenizer prop = new PropertyTokenizer(propertyName);
					if (parameterObject == null) {
						value = null;
					} else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
						value = parameterObject;
					} else if (boundSql.hasAdditionalParameter(propertyName)) {
						value = boundSql.getAdditionalParameter(propertyName);
					} else if (propertyName.startsWith(ForEachSqlNode.ITEM_PREFIX)&& boundSql.hasAdditionalParameter(prop.getName())) {
						value = boundSql.getAdditionalParameter(prop.getName());
						if (value != null) {
							value = configuration.newMetaObject(value).getValue(propertyName.substring(prop.getName().length()));
						}
					} else {
						value = metaObject == null ? null : metaObject.getValue(propertyName);
					}
					TypeHandler typeHandler = parameterMapping.getTypeHandler();
					if (typeHandler == null) {
						throw new ExecutorException("There was no TypeHandler found for parameter "+ propertyName + " of statement "+ mappedStatement.getId());
					}
					typeHandler.setParameter(ps, i + 1, value, parameterMapping.getJdbcType());
				}
			}
		}
	}
	
	/**
	 * 鏍规嵁鏁版嵁搴撴柟瑷�紝鐢熸垚鐗瑰畾鐨勫垎椤祍ql
	 * @param sql
	 * @param page
	 * @return
	 */
	private String generatePageSql(String sql,Page page)
	{
		if(page!=null && dialect!=null && dialect.trim().length()>0){
			StringBuffer pageSql = new StringBuffer();
			if("mysql".equals(dialect)){
				pageSql.append(sql);
				pageSql.append(" limit "+page.getStart()+","+page.getPageShow());
				System.out.println("mysql split page resutl SQL:   "+pageSql.toString());
			}else if("oracle".equals(dialect)){
				pageSql.append("select * from (select tmp_tb.*,ROWNUM row_id from (");
				pageSql.append(sql);
				pageSql.append(") as tmp_tb where ROWNUM<=");
				pageSql.append(page.getStart()+page.getPageShow());
				pageSql.append(") where row_id>");
				pageSql.append(page.getStart());
			}
			return pageSql.toString();
		}else{
			return sql;
		}
	}
	
	public Object plugin(Object arg0) 
	{
		return Plugin.wrap(arg0, this);
	}

	public void setProperties(Properties p) 
	{
		dialect = p.getProperty("dialect");
//		if (dialect!=null && dialect.trim().length()>0) {
//			System.out.println("dialect property is not found!");
//		}
		pageSqlId = p.getProperty("pageSqlId");
//		if (pageSqlId!=null && pageSqlId.trim().length()>0) {
//				System.out.println("pageSqlId property is not found!");
//		}
	}
	
}
