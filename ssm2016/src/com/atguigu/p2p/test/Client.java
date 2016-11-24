package com.atguigu.p2p.test;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.UUID;

import javax.annotation.Resource;

import org.apache.commons.lang.math.RandomUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Service;

import com.atguigu.p2p.dao.ICustomerDAO;
import com.atguigu.p2p.vo.CustomerModel;
import com.atguigu.p2p.vo.CustomerQueryModel;


@Service
public class Client 
{
	//@Autowired	//鎸夌収绫诲瀷瑁呴厤,鏈夊畠鐨勮瘽锛宎pplicationContext.xml鏂囦欢閲屼笉闇�鍐嶉厤缃�property name="userDao" ref="userDao"/>
					//java绫婚噷闈篃涓嶅啀闇�閰嶇疆setter鏂规硶
					//applicationContext.xml閰嶇疆鏂囦欢閲岄潰鏃爌roperty灞炴�+绫婚噷闈㈡棤setter鏂规硶le
	
	@Resource		//鎸夌収鍚嶇О瑁呴厤锛屾壘涓嶅埌鍚嶇О鍚庡啀鎸夌収绫诲瀷瑁呴厤
	private ICustomerDAO dao = null;
	
	public static void main(String[] args) 
	{
		ApplicationContext ctx = new ClassPathXmlApplicationContext("applicationContext.xml");
		Client t = (Client)ctx.getBean("client");
		
		int i = 66;
		CustomerModel cm = new CustomerModel();
		cm.setCustomerId("cid"+RandomUtils.nextInt());
		cm.setPwd(UUID.randomUUID().toString().substring(26));
		cm.setRegisterTime(new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()));
		cm.setShowName("CA:"+i);
		cm.setTrueName("寮�"+i);		
		
		t.dao.create(cm);

		//===============================================
		CustomerQueryModel cqm2 = new CustomerQueryModel();
		cqm2.getPage().setNowPage(2);
		List<CustomerModel> list2 = t.dao.getByConditionPage(cqm2);
		System.out.println(cqm2.getPage());
		for (CustomerModel customerModel : list2) 
		{
			System.out.println(customerModel.toString());
		}
		
	}
}
