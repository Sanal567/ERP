package com.isgn.massTransaction.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.isgn.massTransaction.DAOImpl;
import com.isgn.massTransaction.exceptions.MassTransactionsException;
import com.isgn.massTransaction.exceptions.MassTransactionsExceptionBuilder;
import com.isgn.massTransaction.exceptions.MassTransactionsExceptionConstants;
import com.isgn.massTransaction.model.Clientconfig;
import com.isgn.massTransaction.model.FileUpload;
import com.isgn.massTransaction.model.MassTransactionEntity;
import com.isgn.massTransaction.model.ResetPassword;
import com.isgn.massTransaction.model.Roles;
import com.isgn.massTransaction.model.Users;
import com.isgn.massTransaction.repository.MassTransactionRepository;
import com.isgn.massTransaction.service.UserService;
import com.isgn.massTransaction.validator.FileValidator;
import com.isgn.massTransaction.validator.PasswordValidator;
import com.isgn.massTransaction.validator.UserValidator;

@Controller
public class UserController {

	private static Logger LOGGER = LogManager.getLogger(UserController.class.getName());

	@Autowired
	private UserService userService;
	@Autowired
	private UserValidator userValidator;
	@Autowired
	private PasswordValidator passwordValidator;
	@Autowired
	private FileValidator fileValidator;
	@Autowired
	private MassTransactionRepository massTransactionRepository;

	private int sum = 0;
	private String OS = System.getProperty("os.name").toLowerCase();

	@RequestMapping(value = "/registration", method = RequestMethod.GET)
	public String registration(Model model) {
		try {
			
			String userName = userService.findLoginName();
			Users user = userService.findByUsername(userName);
			Clientconfig clientconfig = userService.findByClientname(user.getClientname());
			model.addAttribute("userForm", new Users());
			
			String userRole = userService.findRole();
			if (userRole.equals("ROLE_ADMIN")) {
				LOGGER.info("User role :" + userRole);
				model.addAttribute("clientname", clientconfig.getClientname());
				return "registration";
			} else {
				LOGGER.info("User role is: " + userRole);
				return "login";
			}
		} catch (NullPointerException nullPointerException) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder().build(
					MassTransactionsExceptionConstants.NULL_POINTER_EXCEPTION, null,
					nullPointerException.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::NullPointerException occured:::::::" + massTransactionsException);
			return "login";
		}
	}

	@RequestMapping(value = "/registration", method = RequestMethod.POST)
	public String registration(HttpServletRequest request, @ModelAttribute("userForm") Users userForm,
			BindingResult bindingResult, Model model) {
		try {
			userValidator.validate(userForm, bindingResult);
			Roles role = new Roles();
			role.setRolename("ROLE_USER");
			Set<Roles> roles = new HashSet<Roles>();
			roles.add(role);
			userForm.setRoles(roles);
			String userRole = userService.findRole();
			String userName = userService.findLoginName();
			Users user = userService.findByUsername(userName);
			Clientconfig clientconfig = userService.findByClientname(user.getClientname());
			userForm.setClientname(user.getClientname());
			if (bindingResult.getErrorCount() > 0) {
				model.addAttribute("clientname", clientconfig.getClientname());
				return "registration";
			}
			if (userRole != null) {
				userService.save(userForm, userRole);
				userService.generatePasswordLink(userForm, request, "ROLE_USER");
			} else {
				return "redirect:/login";
			}

			return "redirect:/adminSuccess";
		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured::::::::::::::::" + massTransactionsException);
			return "redirect:/login";
		}
	}

	// customize the error message
	private String getErrorMessage(HttpServletRequest request, String key) {
		Exception exception = (Exception) request.getSession().getAttribute(key);
		String error = "";

		if (exception instanceof BadCredentialsException)
			error = "Invalid username and password!";
		else if (exception instanceof LockedException)
			error = exception.getMessage();
		else
			error = "Invalid username and password!";

		return error;
	}

	@RequestMapping(value = { "/", "/login" }, method = RequestMethod.GET)
	public String login(HttpServletRequest request, @RequestParam(value = "error", required = false) String error,
			@RequestParam(value = "logout", required = false) String logout, Model model) {
		try {
			if (error != null) {
				LOGGER.info("error : " + error);
				LOGGER.info("error : " + getErrorMessage(request, "SPRING_SECURITY_LAST_EXCEPTION"));
				model.addAttribute("error", getErrorMessage(request, "SPRING_SECURITY_LAST_EXCEPTION"));
			}
			if (logout != null)
				model.addAttribute("message", "You have been logged out successfully.");

			String userRole = userService.findRole();
			if (userRole == null) {
				return "login";
			}

			String userName = userService.findLoginName();
			Users user = userService.findByUsername(userName);
			boolean status = user.isObsolete();
			boolean isDeleted = user.isDeleted();
			Clientconfig clientconfig1 = userService.findByClientname(user.getClientname());

			if (userService.isAccountLocked(userName) >= 3) {
				Date dbDate = user.getBlockedupto();
				Date current = new Date();
				if (dbDate.before(current)) {
					userService.unLockUser(userName, 0, false);

					if (userRole.equals("ROLE_USER")) {
						getListOfFiles(model, "SUBMITTED");
						return "submittedFiles";
					}
					if (userRole.equals("ROLE_ADMIN")) {
						model.addAttribute("clientname", clientconfig1.getClientname());
						model.addAttribute("userForm", new Users());
						return "registration";
					}

				} else {
					model.addAttribute("error", "account locked contact system admin");
					return "login";
				}
			}

			else if (isDeleted) {
				model.addAttribute("error", "invalid account");
				return "login";
			}

			else if (status) {
				model.addAttribute("error", "user inactive contact sys admin");
				return "login";
			} else {

				Clientconfig clientconfig = userService.findByClientname(user.getClientname());

				if (userRole.equals("ROLE_USER")) {
					getListOfFiles(model, "SUBMITTED");
					userService.unLockUser(userName, 0, false);
					return "submittedFiles";
				}
				if (userRole.equals("ROLE_ADMIN")) {
					model.addAttribute("clientname", clientconfig.getClientname());
					model.addAttribute("userForm", new Users());
					userService.unLockUser(userName, 0, false);
					return "registration";
				}

				return "login";
			}
		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured::::::::::::::::" + massTransactionsException);
			return "login";
		}
		return "login";

	}

	/*
	 * @RequestMapping(value = { "/registrationOne" }, method = RequestMethod.POST)
	 * public String registrationOne(HttpServletRequest request, Model model) {
	 * model.addAttribute("userForm", new Users()); return "registration"; }
	 */

	@RequestMapping(value = { "/adminSuccess" }, method = RequestMethod.GET)
	public String adminSuccess(Model model) {
		try {
			if (userService.findLoginName() == null) {
				return "login";
			}
			Users user = findUserIdInactiveOrDelete();
			if (user.isObsolete() == true) {
				model.addAttribute("username", "user inactive contact sys admin ");
				return "login";
			}
			if (user.isDeleted() == true) {
				model.addAttribute("username", "invalid account");
				return "login";
			} else
				return "adminSuccess";
		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured::::::::::::::::" + massTransactionsException);
			return "login";
		}
	}

	@RequestMapping(value = { "/setPassword" }, method = RequestMethod.GET)
	public String setPassword(@RequestParam(value = "username") String username,
			@RequestParam(value = "token") String token, Model model) {
		Boolean tokenFlag = false;
		Boolean timeFlag = false;
		ResetPassword resetPassword = new ResetPassword();

		try {
			resetPassword.setToken(token.replace("'", ""));
			resetPassword.setUsername(username.replace("'", ""));
			Users users = userService.findByUsername(resetPassword.getUsername());
			Date currentDate = new Date();
			Date linkExpireDate = users.getLinkexpireddate();
			String tokenId = users.getToken();
			if (tokenId.equals(resetPassword.getToken()))
				tokenFlag = true;
			if (currentDate.before(linkExpireDate))
				timeFlag = true;
			model.addAttribute("resetPassword", resetPassword);

			if (tokenFlag && timeFlag)
				return "resetPassword";
			else
				return "tokenExpired";
		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured::::::::::::::::" + massTransactionsException);
			return "login";
		}
	}

	@RequestMapping(value = { "/resetPassword" }, method = RequestMethod.POST)
	public String resetPassword(HttpServletRequest request,
			@ModelAttribute("resetPassword") ResetPassword resetPassword, BindingResult bindingResult, Model model) {
		try {
			passwordValidator.validate(resetPassword, bindingResult);

			if (bindingResult.getErrorCount() > 0)
				return "resetPassword";

			Users users = userService.findByUsername(resetPassword.getUsername().replace("'", ""));
			userService.createPassword(users, resetPassword);
		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured::::::::::::::::" + massTransactionsException);
		}
		return "login";
	}

	@RequestMapping(value = { "/forgotPwd" }, method = RequestMethod.GET)
	public String forgotPwd(Model model) {
		model.addAttribute("userCheck", new Users());
		return "forgotPwd";
	}

	@RequestMapping(value = { "/checkUserName" }, method = RequestMethod.POST)
	public String checkUserName(HttpServletRequest request, @ModelAttribute("userCheck") Users userCheck,
			BindingResult bindingResult, Model model) {
		try {
			userValidator.userCheck(userCheck, bindingResult);
			if (bindingResult.getErrorCount() > 0) {
				return "forgotPwd";
			}
			Users user = userService.findByUsername(userCheck.getUsername());
			if (user.getFailureattemptcount() >= 3) {
				model.addAttribute("username", "account locked contact system admin");
				return "forgotPwd";
			}
			if (user.isDeleted() == true) {
				model.addAttribute("username", "invalid account");
				return "forgotPwd";
			}
			if (user.isObsolete() == true) {
				model.addAttribute("username", "user inactive contact sys admin ");
				return "forgotPwd";
			}

			userService.generatePasswordLink(userCheck, request, "ROLE_USER");
			return "resetPwdMessage";
		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured::::::::::::::::" + massTransactionsException);
			return "login";
		}
	}

	int getEndDate(int month) {

		int days = 31;
		switch (month) {

		case 4:
		case 6:
		case 9:
		case 11:
			days = 31;
			break;

		case 2:
			days = 28;
			break;
		}

		return days;
	}

	@RequestMapping(value = { "/checkstatus" }, method = RequestMethod.GET)
	public String checkTime(@ModelAttribute("timeCheck") MassTransactionEntity timeCheck, Model model) {
		try {
			Users users = findUserIdInactiveOrDelete();
			if (userService.findLoginName() == null)
				return "login";

			if (users.isDeleted() == true) {
				model.addAttribute("username", "invalid account");
				return "login";
			}
			if (users.isObsolete() == true) {
				model.addAttribute("username", "user inactive contact sys admin ");
				return "login";
			}
			String pattern = "yyyy-MM-dd";
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
			String fromDate = simpleDateFormat.format(timeCheck.getLastUpdatedOn());
			String toDate = simpleDateFormat.format(timeCheck.getReqireDate());

			String status = timeCheck.getFileStatus();
			// Date lastUpdatedOn =
			// Date requireDate = timeCheck.getReqireDate();
			String userName = userService.findLoginName();
			Users user = userService.findByUsername(userName);
			// ModelAndView model1 = new ModelAndView("home");
			List<MassTransactionEntity> obj = userService.findByFileStatusAndDate(status, fromDate, toDate,
					user.getClientname());
			// int objCount = userService.getCount(status, lastUpdatedOn,
			// requireDate,user.getClientname());

			obj.forEach(mass -> {
				sum = sum + mass.getFieldCount();
			});
			model.addAttribute("clientName", user.getClientname());
			model.addAttribute("massTxnLog", obj);
			model.addAttribute("home1", obj.size());
			model.addAttribute("home2", sum);

			/*
			 * LOGGER.info("obj=======" + obj); model1.addObject("billingData", obj);
			 * model1.addObject("home1", obj.size()); model1.addObject("home2", sum);
			 */
			sum = 0;
			return "home";
		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured::::::::::::::::" + massTransactionsException);
			return "login";
		}

	}

	@RequestMapping(value = { "/successEnter" }, method = RequestMethod.GET)
	public String dbCheck(Model model) {
		try {
			if (userService.findLoginName() == null)
				return "login";

			else {
				String userName = userService.findLoginName();
				Users user = userService.findByUsername(userName);
				Clientconfig clientconfig = userService.findByClientname(user.getClientname());
				model.addAttribute("clientname", clientconfig.getClientname());
				model.addAttribute("timeCheck", new MassTransactionEntity());
				return "successEnter";
			}
		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured::::::::::::::::" + massTransactionsException);
			return "login";
		}
	}

	@RequestMapping(value = { "/countuser" }, method = RequestMethod.GET)
	public String checkcountuser(@ModelAttribute("timeCheck1") MassTransactionEntity timeCheck1,
			BindingResult bindingResult, Model model) {
		try {
			String pattern = "yyyy-MM-dd";
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
			String fromDate = simpleDateFormat.format(timeCheck1.getLastUpdatedOn());
			String toDate = simpleDateFormat.format(timeCheck1.getReqireDate());
			if (userService.findLoginName() == null)
				return "login";

			Users users = findUserIdInactiveOrDelete();
			if (users.isDeleted() == true) {
				model.addAttribute("error", "invalid account");
				return "login";
			}
			if (users.isObsolete() == true) {
				model.addAttribute("error", "user inactive contact sys admin ");
				return "login";
			}
			String status = timeCheck1.getFileStatus();
			/*
			 * Date lastUpdatedOn = timeCheck1.getLastUpdatedOn(); Date requireDate =
			 * timeCheck1.getReqireDate();
			 */
			String userName = "";
			String userRole = userService.findRole();
			if (userRole.equals("ROLE_USER")) {
				userName = userService.findLoginName();
			} else {
				userName = timeCheck1.getSubmittedBy();
			}
			Users user = userService.findByUsername(userName);

			if (user == null) {
				bindingResult.rejectValue("submittedBy", "usercount.username");
				return "UserCount";
			} else {

				List<MassTransactionEntity> obj = userService.findByFileStatusAndDateAndUserID(status, userName,
						fromDate, toDate, user.getClientname());
				// int objCount = userService.getCount(status, userName,
				// lastUpdatedOn,
				// requireDate,user.getClientname());

				obj.forEach(mass -> {
					sum = sum + mass.getFieldCount();
				});

				/*
				 * model1.addObject("home", obj); model1.addObject("home1", obj.size());
				 * model1.addObject("home2", sum);
				 */
				model.addAttribute("clientName", user.getClientname());
				model.addAttribute("massTxnLog", obj);
				model.addAttribute("home1", obj.size());
				model.addAttribute("home2", sum);
				sum = 0;
				return "home";
			}
		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured::::::::::::::::" + massTransactionsException);
			return "login";
		}

	}

	// UserCount redirect page
	@RequestMapping(value = { "/UserCount" }, method = RequestMethod.GET)
	public String MontlyCount(Model model) {
		try {
			if (userService.findLoginName() == null) {
				LOGGER.info("user is null");
				return "login";
			} else {
				String userName = userService.findLoginName();
				Users user = userService.findByUsername(userName);
				Clientconfig clientconfig = userService.findByClientname(user.getClientname());
				model.addAttribute("clientname", clientconfig.getClientname());
				model.addAttribute("timeCheck1", new MassTransactionEntity());
				return "UserCount";
			}
		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured ::::::::::::::::" + massTransactionsException);
			return "login";
		}

	}

	@RequestMapping(value = { "/uploadFile" }, method = RequestMethod.POST)
	public @ResponseBody String uploadFileHandler(HttpServletRequest request,
			@ModelAttribute("fileUpload") String filename) {
		return filename;
	}

	@PostMapping("/upload")
	// new annotation since 4.3
	public String singleFileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes,
			@ModelAttribute("fileForm") FileUpload fileUpload, BindingResult bindingResult, Model model) {
		try {
			Users users = findUserIdInactiveOrDelete();
			if (users.isDeleted() == true) {
				model.addAttribute("error", "invalid account");
				return "login";
			}
			if (users.isObsolete() == true) {
				model.addAttribute("error", "user inactive contact sys admin ");
				return "login";
			}
			String userName = userService.findLoginName();
			Users user = userService.findByUsername(userName);
			Clientconfig clientconfig = userService.findByClientname(user.getClientname());

			fileValidator.validate(file, bindingResult, clientconfig.getLocation(), user.getClientname());

			String fileName = file.getOriginalFilename();
			if (bindingResult.getErrorCount() > 0) {
				model.addAttribute("clientname", clientconfig.getClientname());
				return "upload";
			} else {
				/* file.transferTo(new File(clientconfig.getLocation() + filename)); */
				/* try { */
				DAOImpl daoImpl = new DAOImpl();
				daoImpl.insertTxnDetails(userName, new java.sql.Timestamp(System.currentTimeMillis()), userName,
						new java.sql.Date(System.currentTimeMillis()), fileName, "SUBMITTED", 0, "",
						user.getClientname());
				redirectAttributes.addFlashAttribute("message",
						"File " + file.getOriginalFilename() + " is successfully uploaded");
				model.addAttribute("clientname", clientconfig.getClientname());
				/*
				 * } catch (MassTransactionsException e) {
				 */
				/* } */
				return "fileUploadSuccess";
			}
		} catch (Exception e) {
			bindingResult.rejectValue("file", "duplicate.filename");

			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured in singleFileUpload ::::::::::::::::"
					+ massTransactionsException);
			return "upload";
		}

	}

	@RequestMapping(value = { "/upload" }, method = RequestMethod.GET)
	public String uploadFile(HttpServletRequest request, Model model) {
		if (userService.findLoginName() == null) {
			LOGGER.info("username is null");
			return "login";
		}

		else {
			try {
				String userName = userService.findLoginName();
				Users user = userService.findByUsername(userName);
				Clientconfig clientconfig = userService.findByClientname(user.getClientname());
				model.addAttribute("clientname", clientconfig.getClientname());
			} catch (Exception e) {
				MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
						.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
				LOGGER.error("::::::::::::::::::: Exception occured ::::::::::::::::" + massTransactionsException);
			}
			return "upload";
		}
	}

	// This controller shows the about Page
	@RequestMapping(value = { "/about" }, method = RequestMethod.GET)
	public String about(HttpServletRequest request, Model model) {

		if (userService.findLoginName() == null) {
			LOGGER.info("username is null");
			return "login";
		}

		else {
			try {
				String userName = userService.findLoginName();
				Users users = userService.findByUsername(userName);
				Clientconfig clientconfig = userService.findByClientname(users.getClientname());
				model.addAttribute("clientname", clientconfig.getClientname());
			} catch (Exception e) {
				MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
						.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
				LOGGER.error("::::::::::::::::::: Exception occured while disaplying about ::::::::::::::::"
						+ massTransactionsException);
			}
			return "about";
		}
	}

	public List<MassTransactionEntity> getListOfFiles(Model model, String status) {
		List<MassTransactionEntity> massTxnLog = new ArrayList<>();

		try {
			String userName = userService.findLoginName();
			Users user = userService.findByUsername(userName);
			String userRole = userService.findRole();
			if (userRole.equals("ROLE_ADMIN"))
				massTxnLog = userService.getFilesForAdminAndClientName(status, user.getClientname());
			else
				massTxnLog = userService.getFilesByUserNameAndStatusAndClientName(user.getUsername(), status,
						user.getClientname());

			Clientconfig clientconfig = userService.findByClientname(user.getClientname());
			model.addAttribute("massTxnLog", massTxnLog);
			model.addAttribute("clientname", clientconfig.getClientname());
		} catch (NullPointerException e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("NullPointerException occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: NullPointerException occured while getting list of files ::::::::::::::::"
					+ massTransactionsException);
		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured while getting list of files ::::::::::::::::"
					+ massTransactionsException);
		}
		return massTxnLog;
	}

	// This controller shows the submitted files list in jsp
	@RequestMapping(value = "/submittedFiles", method = RequestMethod.GET)
	public String ProcessedDashboardPage(Model model) {
		if (userService.findLoginName() == null) {
			LOGGER.info("username is null");
			return "login";
		} else {
			getListOfFiles(model, "SUBMITTED");
			return "submittedFiles";
		}

	}

	// This controller shows the errorFiles files list in jsp
	@RequestMapping(value = "/errorFiles", method = RequestMethod.GET)
	public String rejectedDashboardPage(Model model) {
		if (userService.findLoginName() == null) {
			LOGGER.info("username is null");
			return "login";
		} else {
			getListOfFiles(model, "REJECTED");
			return "errorFiles";
		}
	}

	// This controller shows the processedFiles files list in jsp
	@RequestMapping(value = "/processedFiles", method = RequestMethod.GET)
	public String submittedDashboardPage(Model model) {

		if (userService.findLoginName() == null) {
			LOGGER.info("user isname null");
			return "login";
		} else {
			getListOfFiles(model, "PROCESSED");
			return "processedFiles";
		}

	}

	@RequestMapping(value = "/useradmin", method = RequestMethod.GET)
	public String getActiveUsers(Model model) {
		if (userService.findLoginName() == null) {
			LOGGER.info("user is null");
			return "login";
		} else {
			try {
				String userName = userService.findLoginName();
				Users users = userService.findByUsername(userName);
				Clientconfig clientconfig = userService.findByClientname(users.getClientname());
				List<Users> userList = userService.findAllUsers(userName, false, clientconfig.getClientname());
				model.addAttribute("userList", userList);
			} catch (Exception e) {
				MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
						.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
				LOGGER.error("::::::::::::::::::: Exception occured while getting Active users::::::::::::::::"
						+ massTransactionsException);
			}
		}
		return "userAdmin";
	}

	@RequestMapping(value = "/userStatus", method = RequestMethod.GET)
	public String changeUserStatus(Model model, @RequestParam("username") String username) {

		Date date = new Date();
		int wrongAttempt = 0;
		try {
			LOGGER.info("recieved username " + username);
			if (userService.findLoginName() == null) {
				LOGGER.info("user is null");
				return "login";
			} else {
				Users user = userService.findByUsername(username);
				boolean obsoleteflag = true;
				if (user.isObsolete()) {
					obsoleteflag = false;
					wrongAttempt = 0;
				}
				userService.updateUserStatus(user.getUserid(), obsoleteflag, date, wrongAttempt);
				String userName = userService.findLoginName();
				Users users = userService.findByUsername(userName);
				Clientconfig clientconfig = userService.findByClientname(users.getClientname());
				List<Users> userList = userService.findAllUsers(userName, false, clientconfig.getClientname());
				model.addAttribute("userList", userList);
			}
		} catch (NullPointerException e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("NullPointerException", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: NullPointerException occured user statistic method ::::::::::::::::"
					+ massTransactionsException);

		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured in user statistics method::::::::::::::::"
					+ massTransactionsException);
		}
		return "redirect:useradmin";
	}

	@RequestMapping(value = "/history", method = RequestMethod.GET)
	public String fetchfiles(HttpServletRequest request, Model model) {
		try {
			Users user = findUserIdInactiveOrDelete();
			if (userService.findRole() == null)
				return "login";

			if (user.isDeleted() == true) {
				model.addAttribute("username", "invalid account");
				return "login";
			}
			if (user.isObsolete() == true) {
				model.addAttribute("username", "user inactive contact sys admin ");
				return "login";
			}

			else {
				String userRole = userService.findRole();
				String userName = userService.findLoginName();
				Users users = userService.findByUsername(userName);
				model.addAttribute("clientName", users.getClientname());
				List<MassTransactionEntity> massTxnLog = new ArrayList();

				if (userRole.equals("ROLE_USER")) {
					massTxnLog = userService.getAllFilesByUserNameAndClientName(users.getUsername(),
							users.getClientname());
					model.addAttribute("massTxnLog", massTxnLog);
					return "userHistory";
				} else {
					massTxnLog = userService.getAllFilesAndClientName(users.getClientname());
					model.addAttribute("massTxnLog", massTxnLog);
					return "history";
				}
			}
		} catch (NullPointerException e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("NullPointerException", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: NullPointerException occured in history method ::::::::::::::::"
					+ massTransactionsException);

		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured in history method::::::::::::::::"
					+ massTransactionsException);
		}
		return null;
	}

	@RequestMapping(value = "/download", method = RequestMethod.GET)
	public String dowload(Model model, @RequestParam("fileId") Integer fileId, HttpServletRequest request,
			HttpServletResponse response) {
		String fullPath = null;
		String fileSeparator = null;
		try {
			Users users = findUserIdInactiveOrDelete();
			if (users.isDeleted() == true) {
				model.addAttribute("error", "invalid account");
				return "login";
			}
			if (users.isObsolete() == true) {
				model.addAttribute("error", "user inactive contact sys admin ");
				return "login";
			}
			String userName = userService.findLoginName();
			Users user = userService.findByUsername(userName);
			Clientconfig clientconfig = userService.findByClientname(user.getClientname());
			MassTransactionEntity massTxnDto = massTransactionRepository.findByFileId(fileId);
			String fileLocation = clientconfig.getLocation();

			if (isWindows()) {
				LOGGER.info("This is Windows OS");
				fileSeparator = "\\";
				fileLocation = fileLocation.substring(0, fileLocation.length() - 2);
			} else if (isUnix()) {
				LOGGER.info("This is Unix or Linux OS");
				fileSeparator = "/";
				fileLocation = fileLocation.substring(0, fileLocation.length() - 1);
			}

			LOGGER.info("fileloacation>>>>" + fileLocation);
			String fileStatus = massTxnDto.getFileStatus();
			if (fileStatus.equalsIgnoreCase("SUBMITTED"))
				fullPath = fileLocation;
			if (fileStatus.equalsIgnoreCase("REJECTED"))
				fullPath = fileLocation + "_failed";
			if (fileStatus.equalsIgnoreCase("PROCESSED"))
				fullPath = fileLocation + "_" + fileStatus.toLowerCase();
			File downloadFile = new File(fullPath);

			String filepath = downloadFile + fileSeparator + massTxnDto.getFileName();
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			response.setContentType("APPLICATION/OCTET-STREAM");
			response.setHeader("Content-Disposition", "attachment; filename=\"" + massTxnDto.getFileName() + "\"");
			FileInputStream fileInputStream = new FileInputStream(filepath);
			int i;
			while ((i = fileInputStream.read()) != -1) {
				out.write(i);
			}
			fileInputStream.close();
			out.close();
		} catch (NullPointerException e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("NullPointerException", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: NullPointerException occured while downloading file ::::::::::::::::"
					+ massTransactionsException);

		} catch (IOException e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder().build(
					MassTransactionsExceptionConstants.IO_EXCEPTION, null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: IOException occured while downloading file ::::::::::::::::"
					+ massTransactionsException);
			return "history";

		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured while downloading file::::::::::::::::"
					+ massTransactionsException);
		}
		return "history";
	}

	// Identifying the OS
	public boolean isWindows() {
		return (OS.indexOf("win") >= 0);
	}

	public boolean isMac() {
		return (OS.indexOf("mac") >= 0);
	}

	public boolean isUnix() {
		return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0);
	}

	@RequestMapping(value = "/deleteUser", method = RequestMethod.GET)
	public String deleteUser(Model model, @RequestParam("username") String username) {
		try {
			if (username != null) {
				LOGGER.info("========== recieved username ============" + username);
				if (userService.findLoginName() == null) {
					LOGGER.info("=========== Logged in username is null =============");
					return "login";
				} else {
					Users user = userService.findByUsername(username);
					boolean deleted = false;
					if (!user.isDeleted()) {
						deleted = true;
					}
					userService.deleteUser(user.getUserid(), deleted);
					String userName = userService.findLoginName();
					Users users = userService.findByUsername(userName);
					Clientconfig clientconfig = userService.findByClientname(users.getClientname());
					List<Users> userList = userService.findAllUsers(userName, false, clientconfig.getClientname());
					model.addAttribute("userList", userList);
				}
			}

		} catch (NullPointerException e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("NullPointerException occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: NullPointerException occured while deleting user::::::::::::::::"
					+ massTransactionsException);
		} catch (Exception e) {
			MassTransactionsException massTransactionsException = new MassTransactionsExceptionBuilder()
					.build("Exception occured", null, e.getLocalizedMessage(), Locale.ENGLISH);
			LOGGER.error("::::::::::::::::::: Exception occured while deleting user::::::::::::::::"
					+ massTransactionsException);
		}
		return "redirect:useradmin";
	}

	public Users findUserIdInactiveOrDelete() {
		String userName = userService.findLoginName();
		return userService.findByUsername(userName);
	}

}
