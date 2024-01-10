package com.concerto.crud.common.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.concerto.crud.common.bean.Bean;
import com.concerto.crud.common.bean.Entity;
import com.concerto.crud.common.bean.Field;
import com.concerto.crud.common.bean.Module;
import com.concerto.crud.common.constant.AppConstant;
import com.concerto.crud.common.dao.CommonDAO;
import com.concerto.crud.common.init.AppInitializer;
import com.concerto.crud.common.util.JsonToJavaConverter;
import com.concerto.crud.common.util.Logging;
import com.concerto.crud.common.validationservice.ValidationService;

/**
 * Copyright (C) Concerto Software and Systems (P) LTD | All Rights Reserved
 * 
 * @File : com.concerto.crud.common.service.CommonServiceImpl.java
 * @Author : Suyog Kedar
 * @AddedDate : October 03, 2023 12:30:40 PM
 * @Purpose : Service class providing methods for common operations on a
 *          specified module. Uses data validation, JSON conversion, and
 *          database interactions to perform create, update, read, delete, and
 *          search operations, as well as approval and rejection controls.
 *          Utilizes the CommonModuleDAO interface for database access.
 * @Version : 1.0
 */

@Service
public class CommonServiceImpl implements CommonService {

	@Autowired
	private CommonDAO commonDAO;

	@Autowired
	private ValidationService validationService;

	private static Properties configProperties = AppInitializer.getProps();

	/**
	 * Retrieves data for a specified module based on the provided field name and
	 * value.
	 *
	 * @param fieldName
	 *            The name of the field used for filtering the data.
	 * @param value
	 *            The value to match in the specified field.
	 * @param moduleName
	 *            The name of the module for which data is requested.
	 * @return A map with the retrieved data or an error message if the operation
	 *         fails.
	 */
	@Override
	public List<Map<String, Object>> getData(String fieldName, Object value, String moduleName) {
		Module module = JsonToJavaConverter.moduleData(moduleName);
		List<Map<String, Object>> response = new ArrayList<>();
		List<String> primaryFields = JsonToJavaConverter.getPrimaryfields(moduleName);

		try {

			for (String field : primaryFields) {
				if (field.equals(fieldName)) {
					List<Map<String, Object>> dataRetrieve = commonDAO.executeGetData(fieldName, value, module);
					if (!dataRetrieve.isEmpty()) {
						return dataRetrieve;
					} else {
						Map<String, Object> message = new HashMap<>();
						message.put(AppConstant.COMMON_MODULE_MESSAGE, AppConstant.DATA_NOT_AVAILABLE);
						response.add(message);
						return response;
					}
				}
			}
			Map<String, Object> message = new HashMap<>();
			message.put(AppConstant.COMMON_MODULE_MESSAGE, AppConstant.NOT_PRIMARY_KEY);
			response.add(message);
		} catch (Exception e) {
			Logging.error(AppConstant.DATA_READING_ERROR, e);
		}
		return response;
	}

	/**
	 * Retrieves all data for a specified module.
	 *
	 * @param moduleName
	 *            The name of the module for which all data is requested.
	 * @return A list of maps with all the retrieved data or an error message if the
	 *         operation fails.
	 */
	@Override
	public List<Map<String, Object>> getAllData(String moduleName) {
		Module module = JsonToJavaConverter.moduleData(moduleName);
		List<Map<String, Object>> dataRetrieve = commonDAO.executeGetAllData(module);
		if (dataRetrieve.isEmpty()) {
			Map<String, Object> message = new HashMap<>();
			message.put(AppConstant.COMMON_MODULE_MESSAGE, AppConstant.DATA_NOT_AVAILABLE_IN_MODULE + moduleName);
			dataRetrieve.add(message);
		}
		return dataRetrieve;
	}

	/**
	 * Performs create, update, or delete (CUD) operations for a specified module
	 * based on the provided request.
	 *
	 * @param requestBody
	 *            A map containing the data for the CUD operation.
	 * @param moduleName
	 *            The name of the module for which the CUD operation is performed.
	 * @param request
	 *            The type of CUD operation to be executed (create, update, or
	 *            delete).
	 * @return A map with the result of the CUD operation or an error message if the
	 *         operation fails.
	 */
	@Override
	public Map<String, Object> doCUDprocess(Map<String, Object> requestBody, String moduleName, String request) {
		Map<String, Object> result = new HashMap<>();
		String response = null;
		Module module = JsonToJavaConverter.moduleData(moduleName);
		Map<String, String> validationResult = validationService.checkValidation(module, requestBody);
		List<Field> fields = module.getFields();

		if (!validationResult.isEmpty()) {
			return new HashMap<>(validationResult);
		}
		if (AppConstant.TRANSACTION_WORKFLOW.equalsIgnoreCase(moduleName)) {
			boolean containsWorkflowDescription = false;
			for (Field field : fields) {
				if (AppConstant.FLOW_NAME.equalsIgnoreCase((field.getName()))) {
					containsWorkflowDescription = true;
					break; // Exit the loop once the field is found
				}
			}
			String moduleDecouplingValue = configProperties.getProperty(AppConstant.MODULE_DECOUPLING);
			boolean moduleDecoupling = Boolean.parseBoolean(moduleDecouplingValue);

			if (containsWorkflowDescription && moduleDecoupling) {
				String workflowDescription = requestBody.get(AppConstant.FLOW_NAME).toString();
				String[] flowName = workflowDescription.split(AppConstant.UNDERSCORE);
				requestBody.put(AppConstant.LNET_NAME, flowName[0]);
				requestBody.put(AppConstant.CHANNEL_NAME, flowName[1]);
				requestBody.put(AppConstant.TERM_FIID, flowName[2]);
				requestBody.put(AppConstant.CARD_FIID, flowName[3]);
				requestBody.put(AppConstant.END_POINT_NAME, flowName[4]);
			}

		}
		List<Bean> beanList = module.getBeans();

		response = (beanList != null) ? handleCUDWithBeans(requestBody, module, request)
				: handleCUDWithoutBeans(requestBody, module, request);
		result.put(AppConstant.COMMON_MODULE_MESSAGE,
				AppConstant.SUCCESS.equals(response) ? AppConstant.ADDED_FOR_APPROVAL + request : response);
		return result;
	}

	private String handleCUDWithoutBeans(Map<String, Object> requestBody, Module module, String request) {
		String response = doCUDprocessForBean(requestBody, module, request);
		boolean result = false;
		if (AppConstant.SUCCESS.equals(response)) {
			result = commonDAO.doCUDOperations(requestBody, null, module, request);
			response = result ? AppConstant.SUCCESS : AppConstant.DATA_INSERTION_FAILED;
		}
		return response;
	}

	/**
	 * Handles CUD operations for beans in the provided request body and the
	 * specified module.
	 *
	 * @param requestBody
	 *            A map containing the data for the CUD operation.
	 * @param module
	 *            The module for which the CUD operation is performed.
	 * @param request
	 *            The type of CUD operation to be executed (create, update, or
	 *            delete).
	 * @return A string indicating the result of the CUD operation for beans.
	 */
	private String handleCUDWithBeans(Map<String, Object> requestBody, Module module, String request) {
		Map<String, Object> parentBody = extractParentFields(requestBody);
		List<Map<String, Object>> beanData = extractBeanList(requestBody);

		String response = doCUDprocessForBean(parentBody, module, request);
		if (AppConstant.SUCCESS.equals(response)) {
			List<Bean> beanList = module.getBeans();
			for (int i = 0; i < beanList.size(); i++) {
				Bean bean = beanList.get(i);
				for (Map<String, Object> childBody : beanData) {
					response = doCUDprocessForBean(childBody, bean, request);
				}
				if (AppConstant.SUCCESS.equals(response)) {
					boolean result = commonDAO.doCUDOperations(parentBody, beanData, module, request);
					response = result ? AppConstant.SUCCESS : AppConstant.DATA_INSERTION_FAILED;
				}
			}
		}
		return response;

	}

	/**
	 * Handles CUD operations for a specific bean in the provided data and entity.
	 *
	 * @param requestBody
	 *            A map containing the data for the CUD operation.
	 * @param entity
	 *            The entity (bean) for which the CUD operation is performed.
	 * @param request
	 *            The type of CUD operation to be executed (create, update, or
	 *            delete).
	 * @return A string indicating the result of the CUD operation for the bean.
	 */
	private String doCUDprocessForBean(Map<String, Object> requestBody, Entity entity, String request) {
		String entityName = entity.getEntityName();
		boolean isSubBean = entity.isSubBean();
		String response = null;
		try {
			List<Map<String, Object>> tempResult = commonDAO.getById(entityName + AppConstant.TEMP_TABLE_SUFFIX,
					requestBody, isSubBean);
			List<Map<String, Object>> masterResult = commonDAO.getById(entityName + AppConstant.MASTER_TABLE_SUFFIX,
					requestBody, isSubBean);
			if (!tempResult.isEmpty()) {
				response = AppConstant.APPROVAL_PENDING;
			} else if (!masterResult.isEmpty() && AppConstant.ADD.equalsIgnoreCase(request)) {
				response = AppConstant.DATA_PRESENT;
			} else if (masterResult.isEmpty()
					&& (AppConstant.UPDATE.equalsIgnoreCase(request) || AppConstant.DELETE.equalsIgnoreCase(request))) {
				response = AppConstant.DATA_NOT_PRESENT;
			} else {
				response = AppConstant.SUCCESS;
			}
		} catch (Exception e) {
			Logging.error(AppConstant.DATA_INSERTION_FAILED, e);
		}
		return response;

	}

	/**
	 * Performs approval or rejection for a specified module based on the provided
	 * action.
	 *
	 * @param requestBody
	 *            A map containing the necessary input data for approval or
	 *            rejection.
	 * @param entityName
	 *            The name of the module for which approval or rejection is
	 *            performed.
	 * @param action
	 *            The action to be performed (approve or reject).
	 * @return A map with the result of the approval or rejection or an error
	 *         message if the operation fails.
	 */
	@Override
	public Map<String, Object> doApproveOrReject(Map<String, Object> requestBody, String entityName, String action) {
		Map<String, Object> response = new HashMap<>();
		Module module = JsonToJavaConverter.moduleData(entityName);
		boolean result = false;
		List<Bean> beans = module.getBeans();
		Map<String, Object> combinedData = new HashMap<>();
		try {
			List<Map<String, Object>> dataList = commonDAO.getById(entityName + AppConstant.TEMP_TABLE_SUFFIX,
					requestBody, false);
			if (dataList.isEmpty()) {
				response.put(AppConstant.COMMON_MODULE_MESSAGE, AppConstant.NO_REQUEST_PENDING);
				return response;
			}

			Map<String, Object> data = dataList.get(0);

			if (AppConstant.DELETE.equalsIgnoreCase(data.get(AppConstant.REQUEST).toString())
					&& AppConstant.RECTIFY.equalsIgnoreCase(action)) {
				response.put(AppConstant.COMMON_MODULE_MESSAGE, AppConstant.RECTIFY_NOT_ALLOWED);
				return response;

			}
			if (!AppConstant.PENDING.equalsIgnoreCase(data.get(AppConstant.STATUS).toString())) {
				response.put(AppConstant.COMMON_MODULE_MESSAGE, AppConstant.PENDING_TO_RECTIFY);
				return response;
			}
			if ((AppConstant.REJECT.equalsIgnoreCase(action) && requestBody.get(AppConstant.REJECT_REMARK) == null)
					|| (AppConstant.RECTIFY.equalsIgnoreCase(action)
							&& requestBody.get(AppConstant.RECTIFY_REMARK) == null)) {
				response.put(AppConstant.COMMON_MODULE_MESSAGE, AppConstant.REMARK_NOT_MENTIONED);
				return response;
			}
			combinedData.put(AppConstant.REJECT_REMARK, requestBody.get(AppConstant.REJECT_REMARK));
			combinedData.put(AppConstant.RECTIFY_REMARK, requestBody.get(AppConstant.RECTIFY_REMARK));
			combinedData.put(AppConstant.PARENT_DATA, data);
			if (beans != null) {
				for (Bean bean : beans) {
					String beanName = bean.getEntityName();
					List<Map<String, Object>> subBeanData = commonDAO.getById(beanName + AppConstant.TEMP_TABLE_SUFFIX,
							requestBody, true);
					combinedData.put(AppConstant.BEAN_DATA, subBeanData);
					result = executeApproveOrRejectAction(module, action, combinedData);

				}
			} else {
				result = executeApproveOrRejectAction(module, action, combinedData);
			}
			if (result) {
				response.put(AppConstant.COMMON_MODULE_MESSAGE, action + AppConstant.ACTION_SUCCESSFUL);
			} else {
				response.put(AppConstant.COMMON_MODULE_MESSAGE, action + AppConstant.ACTION_FAILED);
			}
		} catch (Exception e) {
			Logging.error(action + AppConstant.ACTION_FAILED, e);
		}

		return response;
	}

	/**
	 * Performs approval or rejection action for a specified module based on the
	 * provided data and action.
	 *
	 * @param module
	 *            The module for which approval or rejection is performed.
	 * @param action
	 *            The action to be performed (approve or reject).
	 * @param data
	 *            A map containing the data for the approval or rejection action.
	 * @return A boolean indicating whether the approval or rejection action was
	 *         successful.
	 */
	private boolean executeApproveOrRejectAction(Module module, String action, Map<String, Object> data) {
		boolean result = false;

		if (AppConstant.APPROVE.equalsIgnoreCase(action)) {
			return handleApproveAction(data, module, action);
		} else if (AppConstant.RECTIFY.equalsIgnoreCase(action)) {
			return commonDAO.performUpdate(data, module, AppConstant.TEMP_TABLE_SUFFIX);
		} else if (AppConstant.REJECT.equalsIgnoreCase(action)) {
			return commonDAO.deleteData(data, module, action, AppConstant.TEMP_TABLE_SUFFIX);
		}
		return result;
	}

	/**
	 * Handles the approval action for a specified data, module, and action.
	 *
	 * @param data
	 *            The data for which approval is performed.
	 * @param module
	 *            The module for which approval is performed.
	 * @param action
	 *            The action to be performed (approve).
	 * @return A boolean indicating whether the approval action was successful.
	 */
	@SuppressWarnings("unchecked")
	private boolean handleApproveAction(Map<String, Object> data, Module module, String action) {
		boolean tempDelete = false;
		try {
			Map<String, Object> dataMap = (Map<String, Object>) data.get(AppConstant.PARENT_DATA);
			String request = dataMap.get(AppConstant.REQUEST).toString();
			if (AppConstant.NEW_DATA_ACTION.equalsIgnoreCase(request)) {
				boolean addToMaster = commonDAO.addToMaster(data, module, action);
				if (addToMaster) {
					tempDelete = commonDAO.deleteData(data, module, action, AppConstant.TEMP_TABLE_SUFFIX);
				}
				return addToMaster && tempDelete;
			} else if (AppConstant.DELETE_ACTION.equalsIgnoreCase(request)) {
				boolean masterDelete = commonDAO.deleteData(data, module, action, AppConstant.MASTER_TABLE_SUFFIX);
				if (masterDelete) {
					tempDelete = commonDAO.deleteData(data, module, action, AppConstant.TEMP_TABLE_SUFFIX);
				}
				return masterDelete && tempDelete;
			} else if (AppConstant.UPDATE_ACTION.equalsIgnoreCase(request)) {
				boolean update = commonDAO.performUpdate(data, module, AppConstant.MASTER_TABLE_SUFFIX);
				if (update) {
					tempDelete = commonDAO.deleteData(data, module, action, AppConstant.TEMP_TABLE_SUFFIX);
				}
				return update && tempDelete;
			}
		} catch (Exception e) {
			Logging.error(action + AppConstant.ACTION_FAILED, e);
		}
		return false;
	}

	/**
	 * Extracts a list of beans from the given request body.
	 *
	 * @param requestBody
	 *            The request body containing the beans.
	 * @return A list of maps representing the bean data.
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> extractBeanList(Map<String, Object> requestBody) {
		List<Map<String, Object>> beanList = new ArrayList<>();

		for (Map.Entry<String, Object> entry : requestBody.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (AppConstant.BEANS.equals(key) && value instanceof List) {
				beanList.addAll((List<Map<String, Object>>) value);
			}
		}

		return beanList;
	}

	/**
	 * Extracts parent fields from the given request body, excluding beans.
	 *
	 * @param requestBody
	 *            The request body containing the data.
	 * @return A map containing the parent fields.
	 */
	public Map<String, Object> extractParentFields(Map<String, Object> requestBody) {
		Map<String, Object> parentFields = new HashMap<>();

		for (Map.Entry<String, Object> entry : requestBody.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (!AppConstant.BEANS.equals(key)) {
				parentFields.put(key, value);
			}
		}

		return parentFields;
	}

	/**
	 * Performs rectification for the given module and request body.
	 *
	 * @param requestBody
	 *            The request body containing data for rectification.
	 * @param moduleName
	 *            The name of the module to which the request belongs.
	 * @return A map containing the overall rectification response and status
	 *         message.
	 */
	@Override
	public Map<String, Object> doRectify(Map<String, Object> requestBody, String moduleName) {
		Map<String, Object> response = new HashMap<>();
		Module module = JsonToJavaConverter.moduleData(moduleName);
		boolean result = false;
		List<Bean> beans = module.getBeans();
		Map<String, String> validationResult = validationService.checkValidation(module, requestBody);

		if (!validationResult.isEmpty()) {
			return new HashMap<>(validationResult);
		}
		try {
			List<Map<String, Object>> dataList = commonDAO.getById(moduleName + AppConstant.TEMP_TABLE_SUFFIX,
					requestBody, false);
			if (dataList.isEmpty()) {
				response.put(AppConstant.COMMON_MODULE_MESSAGE, AppConstant.NO_REQUEST_PENDING_TO_RECTIFY);
				return response;
			}
			Map<String, Object> data = dataList.get(0);
			Map<String, Object> dataFromTemp = new HashMap<>();
			dataFromTemp.put(AppConstant.PARENT_DATA, data);
			if (AppConstant.PENDING.equalsIgnoreCase(data.get(AppConstant.STATUS).toString())) {
				response.put(AppConstant.COMMON_MODULE_MESSAGE, AppConstant.NO_REQUEST_PENDING_TO_RECTIFY);
				return response;
			} else if (beans != null) {
				for (Bean bean : beans) {
					response = processrectificationWithBean(bean, requestBody, dataFromTemp, module);
				}

			} else {
				result = commonDAO.doRectify(module, dataFromTemp, requestBody);
				response.put(AppConstant.COMMON_MODULE_MESSAGE,
						result ? AppConstant.RECTIFICATION_SUCCESSFUL : AppConstant.RECTIFICATION_FAILED);

			}
		} catch (Exception e) {
			Logging.error(AppConstant.ERROR_WHILE_RECTIFYING, e);
		}
		return response;
	}

	/**
	 * Performs rectification for a specific bean within the given module and data.
	 *
	 * @param bean
	 *            The Bean object representing the entity to be rectified.
	 * @param requestBody
	 *            The original request body containing data for rectification.
	 * @param data
	 *            The data map containing parent and bean data for rectification.
	 * @param module
	 *            The Module object representing the module to which the bean
	 *            belongs.
	 * @return A map containing the rectification response and status message.
	 */
	private Map<String, Object> processrectificationWithBean(Bean bean, Map<String, Object> requestBody,
			Map<String, Object> data, Module module) {
		String beanName = bean.getEntityName();
		boolean result = false;
		Map<String, Object> response = new HashMap<>();
		try {
			List<Map<String, Object>> subBeanData = commonDAO.getById(beanName + AppConstant.TEMP_TABLE_SUFFIX,
					requestBody, true);
			if (data != null && subBeanData != null) {
				data.put(AppConstant.BEAN_DATA, subBeanData);
				Map<String, Object> parentBody = extractParentFields(requestBody);
				List<Map<String, Object>> beanData = extractBeanList(requestBody);
				Map<String, Object> input = new HashMap<>();
				input.put(AppConstant.PARENT_DATA, parentBody);
				input.put(AppConstant.BEAN_DATA, beanData);

				result = commonDAO.doRectify(module, data, input);
				response.put(AppConstant.COMMON_MODULE_MESSAGE,
						result ? AppConstant.RECTIFICATION_SUCCESSFUL : AppConstant.RECTIFICATION_FAILED);

			} else {
				response.put(AppConstant.COMMON_MODULE_MESSAGE, AppConstant.NO_REQUEST_PENDING);
				return response;
			}
		} catch (Exception e) {
			Logging.error(AppConstant.ERROR_WHILE_RECTIFYING, e);
		}
		return response;
	}
}
