/*******************************************************************************
 * Copyright 2024-2025 Espressif Systems (Shanghai) PTE LTD. All rights reserved.
 * Use is subject to license terms.
 *******************************************************************************/
package com.espressif.idf.ui.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.cdt.debug.core.ICDTLaunchConfigurationConstants;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.ILaunchMode;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.launchbar.core.ILaunchBarManager;
import org.eclipse.launchbar.ui.ILaunchBarUIManager;
import org.eclipse.launchbar.ui.NewLaunchConfigWizard;
import org.eclipse.launchbar.ui.NewLaunchConfigWizardDialog;
import org.eclipse.launchbar.ui.internal.commands.LaunchActiveCommandHandler;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.espressif.idf.core.build.IDFLaunchConstants;
import com.espressif.idf.core.logging.Logger;
import com.espressif.idf.core.util.StringUtil;
import com.espressif.idf.ui.UIPlugin;
import com.espressif.idf.ui.dialogs.SelectDebugConfigDialog;
import com.espressif.idf.ui.dialogs.SelectLaunchConfigDialog;

@SuppressWarnings("restriction")
public class RunActionHandler extends LaunchActiveCommandHandler
{

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException
	{
		try
		{
			ILaunchBarManager launchBarManager = UIPlugin.getService(ILaunchBarManager.class);
			new StopLaunchBuildHandler().stop();
			ILaunchConfiguration config = launchBarManager.getActiveLaunchConfiguration();
			if (config == null)
			{
				return Status.OK_STATUS;
			}

			ILaunchMode launchMode = launchBarManager.getActiveLaunchMode();
			if (launchMode == null)
			{
				return Status.OK_STATUS;
			}
			int returnCode = Window.OK;
			String projectName = config.getAttribute(ICDTLaunchConfigurationConstants.ATTR_PROJECT_NAME,
					StringUtil.EMPTY);
			if (projectName.isBlank())
			{
				Boolean isYes = MessageDialog.openQuestion(Display.getDefault().getActiveShell(),
						Messages.RunActionHandler_NoProjectQuestionTitle,
						Messages.RunActionHandler_NoProjectQuestionText);
				if (isYes)
				{
					ILaunchBarUIManager uiManager = UIPlugin.getService(ILaunchBarUIManager.class);
					uiManager.openConfigurationEditor(launchBarManager.getActiveLaunchDescriptor());
				}
				return Status.CANCEL_STATUS;
			}
			if (launchMode.getIdentifier().equals(ILaunchManager.DEBUG_MODE)
					&& config.getType().getIdentifier().contentEquals(IDFLaunchConstants.RUN_LAUNCH_CONFIG_TYPE))
			{
				List<String> suitableDescNames = findSuitableDescNames(projectName,
						IDFLaunchConstants.DEBUG_LAUNCH_CONFIG_TYPE);
				if (suitableDescNames.isEmpty())
				{
					showMessage(Messages.DebugConfigurationNotFoundMsg);
					return Status.CANCEL_STATUS;
				}
				returnCode = new SelectDebugConfigDialog(Display.getDefault().getActiveShell(), suitableDescNames)
						.open();
			}
			else if (launchMode.getIdentifier().equals(ILaunchManager.RUN_MODE)
					&& config.getType().getIdentifier().contentEquals(IDFLaunchConstants.DEBUG_LAUNCH_CONFIG_TYPE))
			{
				List<String> suitableDescNames = findSuitableDescNames(projectName,
						IDFLaunchConstants.RUN_LAUNCH_CONFIG_TYPE);
				returnCode = new SelectLaunchConfigDialog(Display.getDefault().getActiveShell(), suitableDescNames)
						.open();
			}
			if (returnCode == Window.OK)
			{
				launchBarManager.setActiveLaunchMode(launchMode);
				config = launchBarManager.getActiveLaunchConfiguration();
				DebugUITools.launch(config, launchMode.getIdentifier());
			}

			return Status.OK_STATUS;
		}
		catch (CoreException e)
		{
			return e.getStatus();
		}
	}

	private List<String> findSuitableDescNames(String projectName, String configType)
	{
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		List<ILaunchConfiguration> configList = new ArrayList<>();
		try
		{
			configList = Arrays.asList(
					launchManager.getLaunchConfigurations(launchManager.getLaunchConfigurationType(configType)));

		}
		catch (CoreException e)
		{
			Logger.log(e);
		}
		return configList.stream().filter(config -> {
			try
			{
				return config.getAttribute(ICDTLaunchConfigurationConstants.ATTR_PROJECT_NAME, StringUtil.EMPTY)
						.contentEquals(projectName);
			}
			catch (CoreException e)
			{
				Logger.log(e);
			}
			return false;
		}).map(config -> config.getName()).collect(Collectors.toList());
	}

	private void showMessage(final String message)
	{
		Display.getDefault().asyncExec(new Runnable()
		{
			@Override
			public void run()
			{
				Shell activeShell = Display.getDefault().getActiveShell();
				boolean isYes = MessageDialog.openQuestion(activeShell, Messages.MissingDebugConfigurationTitle,
						message);
				if (isYes)
				{

					NewLaunchConfigWizard wizard = new NewLaunchConfigWizard();
					WizardDialog dialog = new NewLaunchConfigWizardDialog(activeShell, wizard);
					dialog.open();
					try
					{
						wizard.getWorkingCopy().doSave();
					}
					catch (CoreException e)
					{
						Logger.log(e);
					}
				}
			}
		});
	}
}
