package com.zjcds.common.db.au;

import com.zjcds.common.datastore.MetaDataNavigator;
import com.zjcds.common.datastore.create.Column;
import com.zjcds.common.datastore.create.Table;
import com.zjcds.common.datastore.dml.ColumnValue;
import com.zjcds.common.datastore.dml.ColumnValues;
import com.zjcds.common.datastore.enums.DsType;
import com.zjcds.common.datastore.factory.DataStoreFactory;
import com.zjcds.common.datastore.impl.JdbcDatastore;
import com.zjcds.common.db.au.domain.ModulePropertiesConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.metamodel.DataContext;
import org.apache.metamodel.data.DataSet;
import org.apache.metamodel.data.Row;
import org.apache.metamodel.query.FilterItem;
import org.apache.metamodel.query.OperatorType;
import org.apache.metamodel.query.SelectItem;
import org.apache.metamodel.schema.ColumnType;
import org.apache.metamodel.schema.MutableColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

/**
 * created date：2017-08-05
 * @author niezhegang
 */
@Component
@Order(0)
@Setter
@Getter
public class DbVersionUpdater implements ApplicationRunner,ResourceLoaderAware,InitializingBean , ApplicationContextAware {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Map<String,JdbcDatastore> jdbcDatastoreMap = new HashMap<>();

    /**资源装载*/
    private ResourceLoader resourceLoader;
    /**应用上下文*/
    private ApplicationContext applicationContext;
    @Autowired
    private ConversionService conversionService;
    @Autowired
    private DBAutoUpdateProperties dbAutoUpdateProperties;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public DbVersionUpdater() {
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, ModulePropertiesConfig> moduleDBAutoUpdatePropertiesMap = null;
        try {
            moduleDBAutoUpdatePropertiesMap = applicationContext.getBeansOfType(ModulePropertiesConfig.class);
        }
        catch (BeansException e) {
            logger.warn("没有数据库自动升级模块被配置！");
            return;
        }
        if(moduleDBAutoUpdatePropertiesMap != null){
            List<ModulePropertiesConfig> modulePropertyConfigs = new ArrayList<>(moduleDBAutoUpdatePropertiesMap.values());
            modulePropertyConfigs.sort(new Comparator<ModulePropertiesConfig>() {
                @Override
                public int compare(ModulePropertiesConfig o1, ModulePropertiesConfig o2) {
                    Integer order1 = o1.getOrder();
                    if(order1 == null)
                        order1 = Integer.MAX_VALUE;
                    Integer order2 = o2.getOrder();
                    if(order2 == null)
                        order2 = Integer.MAX_VALUE;
                    return Integer.compare(order1,order2);
                }
            });
            dbAutoUpdateProperties.setModulePropertyConfigs(modulePropertyConfigs);
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if(dbAutoUpdateProperties.getAutoExec()) {
            //打印版本升级汇总信息
            logger.debug(dbAutoUpdateProperties.printDbUpdateSummaryInfo());
            //检查版本管理表是否创建
            checkDBVersionTable();
            //执行每个模块的版本升级
            List<ModulePropertiesConfig> modulePropertyConfigs = dbAutoUpdateProperties.getModulePropertyConfigs();
            logger.info("开始数据库表结构自动升级检查...");
            try {
                 for(ModulePropertiesConfig moduleDBAutoUpdateProperty : modulePropertyConfigs) {
                     //执行每个模块的版本升级
                     if(moduleDBAutoUpdateProperty.getAutoExec())
                        executeModuleDbUpdate(moduleDBAutoUpdateProperty);
                     else
                         logger.info("模块{}未配置自动执行，如需使用该模块，请手动执行sql脚本！",moduleDBAutoUpdateProperty.getModuleName());
                 }
                 logger.info("当前系统数据库表自动升级过程已成功完成...");
            } catch (Exception e) {
                logger.error("数据库版本升级失败", e);
                throw new IllegalStateException("数据库版本升级失败", e);
            }
        }
        else {
           logger.info("关闭了数据库脚本自动升级功能，请手动执行脚本！");
        }
    }

    /**
     * 执行一个模块的版本升级
     * @param modulePropertiesConfig
     */
    private void executeModuleDbUpdate(ModulePropertiesConfig modulePropertiesConfig) {
        logger.info("开始执行模块{}的版本升级检查", modulePropertiesConfig.getModuleName());
        CheckResult checkResult = checkModuleDBUpdate(modulePropertiesConfig);
        if (checkResult.needUpdate()) {
            logger.info("开始模块{}数据库表结构自动升级，从版本{}到版本{}",
                    modulePropertiesConfig.getModuleName(),checkResult.getFromVersion(), checkResult.getToVersion());
            //执行一个模块的版本更新
            executeModuleDBUpdate(modulePropertiesConfig,checkResult);
        }
        else {
            logger.info("模块{}当前版本{}已经是最新版本 ", modulePropertiesConfig.getModuleName(),checkResult.getFromVersion());
        }

    }

    /**
     * 检查模块数据库表是否需要版本升级
     * @param modulePropertiesConfig
     * @return
     */
    private CheckResult checkModuleDBUpdate(ModulePropertiesConfig modulePropertiesConfig) {
        JdbcDatastore jdbcDatastore = from(modulePropertiesConfig.getDataSource());
        MetaDataNavigator metaDataNavigator = jdbcDatastore.getMetaDataNavigator();
        CheckResult checkResult = new CheckResult(modulePropertiesConfig.getCurrentVersion(), modulePropertiesConfig.getCurrentVersion());
        Integer fromVersion = fromModuleVersion(modulePropertiesConfig,from(dbAutoUpdateProperties.getVerTableDataSource()));
        checkResult.setFromVersion(fromVersion);
        return checkResult;
    }

    private void executeModuleDBUpdate(ModulePropertiesConfig modulePropertiesConfig, CheckResult checkResult) {
        Integer executeVersion;
        InputStream inputStream = null;
        JdbcDatastore jdbcDatastore = from(modulePropertiesConfig.getDataSource());
        while (checkResult.needUpdate()){
            executeVersion = checkResult.getFromVersion() + 1;
            try {
                inputStream = getModuleVersionFileInputStream(modulePropertiesConfig,executeVersion, jdbcDatastore.getMetaDataNavigator().getDsType());
                LineIterator iterator = IOUtils.lineIterator(inputStream,"UTF-8");
                iterator.forEachRemaining(new Consumer<String>() {
                    private Integer lineNumber = 0;
                    @Override
                    public void accept(String line) {
                        try {
                            lineNumber++;
                            line = prepareProcessLine(line);
                            if (!skipProcess(line)) {
                                logger.trace("执行的sql为：{}",line);
                                jdbcDatastore.getNativeSqlExecutor().update(line);
                            }
                        } catch (SQLException sqlException) {
                            throw new IllegalStateException("执行sql脚本第" + lineNumber + "行出错",sqlException);
                        }
                    }
                    private String prepareProcessLine(String line) {
                        String ret = StringUtils.trim(line);
                        ret = StringUtils.stripEnd(ret, ";");
                        return ret;
                    }

                    private boolean skipProcess(String line) {
                        boolean skip = false;
                        if (StringUtils.startsWithAny(line, "#", "-")) {
                            logger.trace("跳过注释行执行[{}]", line);
                            skip = true;
                        } else if (StringUtils.isBlank(line)) {
                            logger.trace("跳过空行执行");
                            skip = true;
                        }
                        return skip;
                    }
                });
                completeModuleVersionUpdate(modulePropertiesConfig,checkResult,executeVersion);
            }
            catch (IOException e){
                throw new IllegalStateException("数据库脚本升级到版本"+executeVersion+"失败，请通知管理员处理！",e);
            }
            finally {
                IOUtils.closeQuietly(inputStream);
            }
        }
    }

    private void completeModuleVersionUpdate(ModulePropertiesConfig modulePropertiesConfig, CheckResult checkResult, Integer executeVersion) {
        JdbcDatastore jdbcDatastore = from(modulePropertiesConfig.getDataSource());
        jdbcDatastore.getMetaSqlExecutor().update(dbAutoUpdateProperties.getVersionTableName(),
                ColumnValues.newColumnValues().addColumnValue(new ColumnValue(dbAutoUpdateProperties.getVersionFieldName(),executeVersion)),
                new FilterItem(new SelectItem(new MutableColumn(dbAutoUpdateProperties.getModuleFieldName())), OperatorType.EQUALS_TO, modulePropertiesConfig.getModuleName())
                );                ;
        logger.info("模块{}数据库脚本已经升级到版本{}！",modulePropertiesConfig.getModuleName(),executeVersion);
        //执行下一版本
        checkResult.setFromVersion(executeVersion);
    }

    private InputStream getModuleVersionFileInputStream(ModulePropertiesConfig modulePropertiesConfig, Integer executeVersion , DsType dsType) {
        Assert.notNull(dsType,"数据源类型不能为空！");
        Assert.notNull(executeVersion,"执行脚本版本号不能为空！");
        String filePath = "classpath:/" + Paths.get(ModulePropertiesConfig.DefaultSqlScriptDir, modulePropertiesConfig.getModuleName(),dsType.name()+"_"+executeVersion+".sql").toString();
        logger.info("升级脚本路径为{}",filePath);
        try {
            return resourceLoader.getResource(filePath).getInputStream();
        }
        catch (IOException e) {
            throw new IllegalArgumentException("脚本文件不能访问",e);
        }
    }

    private void checkDBVersionTable(){
        JdbcDatastore verTableJdbcDatastore = from(dbAutoUpdateProperties.getVerTableDataSource());
        MetaDataNavigator metaDataNavigator = verTableJdbcDatastore.getMetaDataNavigator();
        //不存在则创建
        if(metaDataNavigator.getTable(dbAutoUpdateProperties.getVersionTableName()) == null){
            createVersionTable(verTableJdbcDatastore);
        }
    }

    /**
     * 创建版本表
     * @param verTableJdbcDatastore
     */
    private void createVersionTable(JdbcDatastore verTableJdbcDatastore){
        //创建版本表
        verTableJdbcDatastore.getMetaSqlExecutor().createTable(Table.newBuilder()
                .tableName(dbAutoUpdateProperties.getVersionTableName())
                .addColumn(Column.newBuilder().name(dbAutoUpdateProperties.getModuleFieldName()).type(ColumnType.VARCHAR).size(30))
                .addColumn(Column.newBuilder().name(dbAutoUpdateProperties.getVersionFieldName()).type(ColumnType.INTEGER))
                .build());
    }

    /**
     * 获取模块数据库脚本的起始版本
     * @param modulePropertiesConfig
     * @param verTableJdbcDatastore
     * @return
     */
    private Integer fromModuleVersion(ModulePropertiesConfig modulePropertiesConfig, JdbcDatastore verTableJdbcDatastore){
        DataContext dataContext = verTableJdbcDatastore.getUpdateableDataContext();
        DataSet dataSet = dataContext.query().from(dataContext.getDefaultSchema(),dbAutoUpdateProperties.getVersionTableName())
                            .select(dbAutoUpdateProperties.getVersionFieldName())
                            .where(dbAutoUpdateProperties.getModuleFieldName()).eq(modulePropertiesConfig.getModuleName())
                            .execute();
        Integer fromVersion = 0;
        List<Row> rows = dataSet.toRows();
        //为空表明未初始化
        if(CollectionUtils.isEmpty(rows)) {
            verTableJdbcDatastore.getMetaSqlExecutor().insert(dbAutoUpdateProperties.getVersionTableName(), ColumnValues.newColumnValues()
                    .addColumnValue(new ColumnValue(dbAutoUpdateProperties.getModuleFieldName(), modulePropertiesConfig.getModuleName()))
                .addColumnValue(new ColumnValue(dbAutoUpdateProperties.getVersionFieldName(), ModulePropertiesConfig.DefaultInitVersion)));
            fromVersion = ModulePropertiesConfig.DefaultInitVersion;
        }
        else if(rows.size() > 1){
            throw new IllegalStateException("版本表"+dbAutoUpdateProperties.getVersionTableName()+"中对应模块"+ modulePropertiesConfig.getModuleName()+"的版本记录数多于一条！");
        }
        else {
            Row row = rows.get(0);
            fromVersion = conversionService.convert(row.getValue(0),Integer.class);
        }
        Assert.notNull(fromVersion ,"获取起始版本出错！");
        return fromVersion;
    }

    private JdbcDatastore from(String dataSource){
        JdbcDatastore jdbcDatastore = jdbcDatastoreMap.get(dataSource);
        if(jdbcDatastore == null) {
            try {
                DataSource dataSourceObject = applicationContext.getBean(dataSource,DataSource.class);
                jdbcDatastore = DataStoreFactory.createJdbcDatastore("系统配置库["+dataSource+"]",dataSourceObject);
                jdbcDatastoreMap.put(dataSource,jdbcDatastore);
            }
            catch (Exception e) {
                throw new IllegalArgumentException("创建["+dataSource+"]的Datastore对象失败！",e);
            }
        }
        return jdbcDatastore;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    private static class CheckResult {
        private Integer fromVersion;
        private Integer toVersion;
        public boolean needUpdate(){
            return toVersion > fromVersion;
        }
    }

}
