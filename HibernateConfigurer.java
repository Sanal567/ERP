package edu.vemaschools.config;

import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

// Enable annotation driven transaction management
@EnableTransactionManagement
@Configuration
@PropertySource({ "classpath:/edu/vemaschools/properties/application-config.properties" })
public class HibernateConfigurer {

	private Environment env;

	public Environment getEnv() {
		return env;
	}

	@Autowired
	public void setEnv(Environment env) {
		this.env = env;
	}

	@Bean(destroyMethod ="close")
	public DataSource dataSource() {
		
		BasicDataSource dataSource = new BasicDataSource();
		dataSource.setDriverClassName(env.getProperty("jdbc.driverClassName"));
		dataSource.setUrl(env.getProperty("jdbc.url"));
		dataSource.setPassword(env.getProperty("jdbc.password"));
		dataSource.setUsername(env.getProperty("jdbc.username"));

		return dataSource;
	}

	private Properties hibernateProperties() {
		return new Properties() {
			private static final long serialVersionUID = 1L;
			{
				// setProperty("hibernate.hbm2ddl.auto", env.getProperty("hibernate.hbm2ddl.auto"));
				setProperty("hibernate.dialect", env.getProperty("hibernate.dialect"));		
				setProperty("hibernate.show_sql", env.getProperty("hibernate.show_sql"));
				setProperty("hibernate.hbm2ddl.auto", env.getProperty("hibernate.hbm2ddl.auto"));
				setProperty("hibernate.ejb.naming_strategy", env.getProperty("hibernate.ejb.naming_strategy"));
				setProperty("hibernate.format_sql", env.getProperty("hibernate.format_sql"));
				setProperty("hibernate.globally_quoted_identifiers", env.getProperty("hibernate.globally_quoted_identifiers"));
			}
		};
	}

	// Configure the entity manager factory bean
	@Bean
	public LocalContainerEntityManagerFactoryBean entityManagerFactory() {

		LocalContainerEntityManagerFactoryBean entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
		entityManagerFactoryBean.setDataSource(dataSource());
		entityManagerFactoryBean.setPackagesToScan("edu.vemaschools.entities");
		entityManagerFactoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		entityManagerFactoryBean.setJpaProperties(hibernateProperties());

		return entityManagerFactoryBean;
	}

	/*
	 * @Bean public LocalSessionFactoryBean sessionFactory() {
	 * LocalSessionFactoryBean sessionFactory=new LocalSessionFactoryBean();
	 * sessionFactory.setDataSource(dataSource());
	 * sessionFactory.setPackagesToScan(new String[]{"edu.vemaschools.entities"});
	 * sessionFactory.setHibernateProperties(hibernateProperties());
	 * 
	 * return sessionFactory; }
	 */
	
	// Configure the transaction manager bean
	@Bean
	public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
		JpaTransactionManager jpaTransactionManager = new JpaTransactionManager();
		jpaTransactionManager.setEntityManagerFactory(entityManagerFactory);
		return jpaTransactionManager;
	}

	// Configure the transaction manager bean
	/*@Bean
	@Autowired
	public HibernateTransactionManager transactionManager(SessionFactory sessionFactory) {
		HibernateTransactionManager txManager = new HibernateTransactionManager();
		txManager.setSessionFactory(sessionFactory);

		return txManager;
	}*/
	
	@Bean
	public PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
		return new PersistenceExceptionTranslationPostProcessor();
	}
}
