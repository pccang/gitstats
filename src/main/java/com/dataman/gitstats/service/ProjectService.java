package com.dataman.gitstats.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.ProjectHook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dataman.gitstats.param.AddProjectParam;
import com.dataman.gitstats.po.ProjectBranchStats;
import com.dataman.gitstats.po.ProjectStats;
import com.dataman.gitstats.repository.CommitStatsRepository;
import com.dataman.gitstats.repository.ProjectBranchStatsRepository;
import com.dataman.gitstats.repository.ProjectRepository;
import com.dataman.gitstats.util.Commnt;
import com.dataman.gitstats.util.GitlabUtil;

@Service
public class ProjectService {
	
	@Autowired
	ProjectRepository projectRepository;
	
	@Autowired
	ProjectBranchStatsRepository projectBranchStatsRepository;
	@Autowired
	CommitStatsRepository commitStatsRepository;
	
	@Autowired
	AsyncTask asyncTask;
	
	@Autowired
	GitlabUtil gitlabUtil;
	/**
	 * @method addProject(添加需要统计的项目)
	 * @return int
	 * @author liuqing
	 * @throws Exception 
	 * @date 2017年9月19日 下午3:12:39
	 */
	public int addProject(AddProjectParam param) throws Exception{
		int SUCCESS=0,EXISTED=1,NOTEXIST=2;
		Calendar cal=Calendar.getInstance();
		//验证是否 存在于 mongodb
		ProjectStats ps = projectRepository.findByNameAndAccountId(param.getName(), param.getAid());
		if(ps != null){
			return EXISTED;
		}
		//验证是否 存在于 gitlab
		GitLabApi gitLabApi= gitlabUtil.getGitLabApi(param.getAid());
		List<Project> projects= gitLabApi.getProjectApi().getProjects(param.getName());
		if(projects.isEmpty()){
			return NOTEXIST;
		}
		Project project= projects.get(0);
		//存库
		ps =new ProjectStats();
		ps.setId(Commnt.createUUID());
		ps.setAccountId(param.getAid());
		ps.setName(param.getName());
		ps.setProId(project.getId());
		ps.setCreatedate(cal.getTime());
		ps.setLastupdate(cal.getTime());
		ps.setWeburl(project.getWebUrl());
		ps.setDsc(project.getDescription());
		boolean check=checkWebhookStats(param.getAid(),project.getId());
		ps.setWebhookstatus(check?1:0);
		projectRepository.insert(ps);
		
		List<ProjectBranchStats> branchs=new ArrayList<ProjectBranchStats>(); 
		for (String branch : param.getBranchs()) {
			ProjectBranchStats pbs= new ProjectBranchStats();
			pbs.setId(Commnt.createUUID());
			pbs.setAccountid(param.getAid());
			pbs.setProjectid(ps.getId());
			pbs.setBranch(branch);
			pbs.setProjectname(ps.getName());
			pbs.setStatus(0);
			pbs.setTotalAddRow(0);
			pbs.setTotalDelRow(0);
			pbs.setTotalRow(0);
			pbs.setCreatedate(cal.getTime());
			pbs.setLastupdate(cal.getTime());
			pbs.setProid(ps.getProId());
			branchs.add(pbs);
		}
		projectBranchStatsRepository.insert(branchs);
		for (ProjectBranchStats projectBranchStats : branchs) {
			asyncTask.initProjectStats(projectBranchStats);
		}
		return SUCCESS;
	}
	
	
	boolean checkWebhookStats(String aid,int pid) throws GitLabApiException{
		boolean flag=false;
		GitLabApi gitLabApi= gitlabUtil.getGitLabApi(aid);
		List<ProjectHook> hooks= gitLabApi.getProjectApi().getHooks(pid);
		if(!hooks.isEmpty()){
			flag= hooks.stream().filter(hook -> hook.getUrl().indexOf("/webHook/receive")>0).findFirst().isPresent();	
		}
		return flag;
	}
	
	public List<ProjectStats> getAll(){
		return projectRepository.findAll();
	}
	
	public int delProject(String id){
		int SUCCESS=0;
		projectRepository.delete(id);
		projectBranchStatsRepository.deleteByProjectid(id);
		commitStatsRepository.deleteByProid(id);
		return SUCCESS;
	}
	
}
