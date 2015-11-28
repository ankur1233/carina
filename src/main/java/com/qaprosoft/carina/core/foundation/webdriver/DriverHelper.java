/*
 * Copyright 2013-2015 QAPROSOFT (http://qaprosoft.com/).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qaprosoft.carina.core.foundation.webdriver;

import com.qaprosoft.carina.core.foundation.crypto.CryptoTool;
import com.qaprosoft.carina.core.foundation.log.TestLogCollector;
import com.qaprosoft.carina.core.foundation.log.TestLogHelper;
import com.qaprosoft.carina.core.foundation.utils.Configuration;
import com.qaprosoft.carina.core.foundation.utils.Configuration.Parameter;
import com.qaprosoft.carina.core.foundation.utils.LogicUtils;
import com.qaprosoft.carina.core.foundation.utils.Messager;
import com.qaprosoft.carina.core.foundation.utils.SpecialKeywords;
import com.qaprosoft.carina.core.foundation.webdriver.decorator.ExtendedWebElement;
import com.qaprosoft.carina.core.gui.AbstractPage;

import net.sourceforge.htmlunit.corejs.javascript.JavaScriptException;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hamcrest.BaseMatcher;
import org.openqa.selenium.*;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.interactions.Action;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DriverHelper - WebDriver wrapper for logging and reporting features. Also it
 * contains some complex operations with UI.
 * 
 * @author Alex Khursevich
 */
public class DriverHelper
{
	protected static final Logger LOGGER = Logger.getLogger(DriverHelper.class);

	protected static final long IMPLICIT_TIMEOUT = Configuration.getLong(Parameter.IMPLICIT_TIMEOUT);
	
	protected static final long EXPLICIT_TIMEOUT = Configuration.getLong(Parameter.EXPLICIT_TIMEOUT);

	protected static final long RETRY_TIME = Configuration.getLong(Parameter.RETRY_INTERVAL);

	protected static Wait<WebDriver> wait;

	protected long timer;

	protected TestLogHelper summary;

	protected WebDriver driver;
	
	protected CryptoTool cryptoTool;

	protected static Pattern CRYPTO_PATTERN = Pattern.compile(SpecialKeywords.CRYPT);
	
	public DriverHelper()
	{
		try
		{
			cryptoTool = new CryptoTool();
		}
		catch (Exception e)
		{
			throw new RuntimeException("CryptoTool not initialized, check arg 'crypto_key_path'!");
		}
		summary = new TestLogHelper(UUID.randomUUID().toString());
	}

	public DriverHelper(WebDriver driver)
	{
		this();
		this.driver = driver;
		
		if (driver == null)
		{
			throw new RuntimeException("WebDriver not initialized, check log files for details!");
		}
		driver.manage().timeouts().implicitlyWait(IMPLICIT_TIMEOUT, TimeUnit.SECONDS);
		initSummary(driver);
	}
	

	// --------------------------------------------------------------------------
	// Base UI interaction operations
	// --------------------------------------------------------------------------

	public void setImplicitTimeout(long implicit_wait){
		getDriver().manage().timeouts().implicitlyWait(IMPLICIT_TIMEOUT, TimeUnit.SECONDS);
	}
	
	public long getImplicitTimeout(){
		return IMPLICIT_TIMEOUT;
	}	
	/**
	 * Initializes test log container dedicated to WebDriver instance.
	 * 
	 * @param driver WebDriver
	 */
	protected void initSummary(WebDriver driver)
	{
		summary = new TestLogHelper(driver);
	}

	protected void initSummary(String sessionId)
	{
		summary = new TestLogHelper(sessionId);
	}

	/**
	 * Check that element present.
	 * 
	 * @param extWebElement ExtendedWebElement
	 * @return element existence status.
	 */
	public boolean isElementPresent(final ExtendedWebElement extWebElement)
	{
		return isElementPresent(extWebElement, EXPLICIT_TIMEOUT);
	}

	/**
	 * Check that element present within specified timeout.
	 * 
	 * @param extWebElement ExtendedWebElement
	 * @param timeout
	 *            - timeout.
	 * @return element existence status.
	 */
	public boolean isElementPresent(final ExtendedWebElement extWebElement, long timeout)
	{
		if (extWebElement == null)
			return false;
		return extWebElement.isElementPresent(timeout);
	}

	public boolean isElementPresent(String controlInfo, final WebElement element)
	{
		return new ExtendedWebElement(element, controlInfo).isElementPresent();
	}
	
	public boolean isElementPresent(String controlInfo, final WebElement element, long timeout)
	{
		return new ExtendedWebElement(element, controlInfo).isElementPresent(timeout);
	}
	
	 /**
     * Method which quickly looks for all element and check that they present during EXPLICIT_TIMEOUT
     *
     * @param elements ExtendedWebElement...
     * @return boolean return true only if all elements present.
     */
    public boolean allElementsPresent(ExtendedWebElement... elements) {
        return allElementsPresent(EXPLICIT_TIMEOUT, elements);
    }

    /**
     * Method which quickly looks for all element and check that they present during timeout sec
     *
     * @param timeout
     * @param elements ExtendedWebElement...
     * @return boolean return true only if all elements present.
     */
    public boolean allElementsPresent(long timeout, ExtendedWebElement... elements) {
        int index = 0;
        boolean present = true;
        int counts = 1;
        while (present && index++ < counts) {
            for (int i = 0; i < elements.length; i++) {
                present = isElementPresent(elements[i], timeout / counts);
                if (!present) {
                    LOGGER.error(elements[i].getNameWithLocator() + " is not present.");
                }
            }
        }
        return present;
    }


    /**
     * Method which quickly looks for all element lists and check that they contain at least one element during IMPLICIT_TIMEOUT
     *
     * @param elements List<ExtendedWebElement>...
     * @return boolean
     */
    public boolean allElementListsAreNotEmpty(List<ExtendedWebElement>... elements) {
        return allElementListsAreNotEmpty(IMPLICIT_TIMEOUT, elements);
    }

    /**
     * Method which quickly looks for all element lists and check that they contain at least one element during timeout
     *
     * @param timeout
     * @param elements List<ExtendedWebElement>...
     * @return boolean return true only if All Element lists contain at least one element
     */
    public boolean allElementListsAreNotEmpty(long timeout, List<ExtendedWebElement>... elements) {
        boolean ret = true;
        int counts = 3;
        for (int i = 0; i < elements.length; i++) {
            boolean present = false;
            int index = 0;
            while (!present && index++ < counts) {
                try {
                    present = isElementPresent(elements[i].get(0), (timeout / counts));
                } catch (Exception e) {
                    present = false;
                }
            }
            ret = (elements[i].size() > 0);
            if (!ret) {
                LOGGER.error("List of elements[" + i + "] from elements " + elements.toString() + " is empty.");
                return false;
            }
        }
        return ret;
    }


    /**
     * Method which quickly looks for any element presence during IMPLICIT_TIMEOUT
     *
     * @param elements
     * @return true if any of elements was found.
     */
    public boolean isAnyElementPresent(ExtendedWebElement... elements) {
        return isAnyElementPresent(IMPLICIT_TIMEOUT, elements);
    }

    /**
     * Method which quickly looks for any element presence during timeout sec
     *
     * @param timeout
     * @param elements ExtendedWebElement...
     * @return true if any of elements was found.
     */
    public boolean isAnyElementPresent(long timeout, ExtendedWebElement... elements) {
        int index = 0;
        boolean present = false;
        int counts = 10;
        while (!present && index++ < counts) {
            for (int i = 0; i < elements.length; i++) {
                present = isElementPresent(elements[i], timeout / counts);
                if (present) {
                    LOGGER.debug(elements[i].getNameWithLocator() + " is present");
                    return true;
                }
            }
        }
        if (!present) {
            LOGGER.error("Unable to find any element from array: "
                    + elements.toString());
            return false;
        }
        return present;
    }

    /**
     * return Any Present Element from the list which present during IMPLICIT_TIMEOUT
     *
     * @param elements
     * @return ExtendedWebElement
     */
    public ExtendedWebElement returnAnyPresentElement(ExtendedWebElement... elements) {
        return returnAnyPresentElement(IMPLICIT_TIMEOUT, elements);
    }

    /**
     * return Any Present Element from the list which present during timeout sec
     *
     * @param timeout
     * @param elements ExtendedWebElement...
     * @return ExtendedWebElement
     */
    public ExtendedWebElement returnAnyPresentElement(long timeout, ExtendedWebElement... elements) {
        int index = 0;
        boolean present = false;
        int counts = 10;
        while (!present && index++ < counts) {
            for (int i = 0; i < elements.length; i++) {
                present = isElementPresent(elements[i], timeout / counts);
                if (present) {
                    LOGGER.debug(elements[i].getNameWithLocator() + " is present");
                    return elements[i];
                }
            }
        }
        if (!present) {
            LOGGER.error("All elements are not present");
            throw new RuntimeException(
                    "Unable to find any element from array: "
                            + elements.toString());
        }
        return new ExtendedWebElement(null, null, null);
    }
	
	@Deprecated
	public boolean isElementPresent(String elementName, final By by, long timeout)
	{
		boolean result;
		final WebDriver drv = getDriver();
		drv.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS);
		wait = new WebDriverWait(drv, timeout, RETRY_TIME);
		try
		{
			wait.until(new ExpectedCondition<Boolean>()
			{
				public Boolean apply(WebDriver drv)
				{
					return !drv.findElements(by).isEmpty() && drv.findElement(by).isDisplayed();
				}
			});
			result = true;
		}
		catch (Exception e)
		{
			result = false;
		}
		drv.manage().timeouts().implicitlyWait(IMPLICIT_TIMEOUT, TimeUnit.SECONDS);
		return result;
	}
	
	@Deprecated
	public boolean isElementPresent(String elementName, final By by)
	{
		return isElementPresent(elementName, by, EXPLICIT_TIMEOUT);
	}	



	/**
	 * Check that element with text present.
	 * 
	 * @param extWebElement
	 * @param text
	 *            of element to check.
	 * @return element with text existence status.
	 */
	public boolean isElementWithTextPresent(final ExtendedWebElement extWebElement, final String text)
	{
		return isElementWithTextPresent(extWebElement, text, EXPLICIT_TIMEOUT);
	}

	/**
	 * Check that element with text present.
	 * 
	 * @param extWebElement
	 * @param text
	 *            of element to check.
	 * @param timeout
	 * @return element with text existence status.
	 */	
	public boolean isElementWithTextPresent(final ExtendedWebElement extWebElement, final String text, long timeout)
	{
		return extWebElement.isElementWithTextPresent(text, timeout);
	}

	/**
	 * Check that element not present on page.
	 * 
	 * @return element non-existence status.
	 */
	public boolean isElementNotPresent(final ExtendedWebElement extWebElement) {
		return isElementNotPresent(extWebElement, EXPLICIT_TIMEOUT);
	}
	public boolean isElementNotPresent(final ExtendedWebElement extWebElement, long timeout)
	{
		return extWebElement.isElementNotPresent(timeout);
	}

	public boolean isElementNotPresent(String controlInfo, final WebElement element)
	{
		return isElementNotPresent(new ExtendedWebElement(element, controlInfo));
	}

	

	/**
	 * Types text to specified element.
	 * 
	 * @param text
	 *            to type.
	 */
	public void type(final ExtendedWebElement extWebElement, String text)
	{
		extWebElement.type(text);
	}

	public void type(String controlInfo, WebElement control, String text)
	{
		type(new ExtendedWebElement(control, controlInfo), text);
	}

	/**
	 * Clicks on element.
	 *
     */
	public void click(final ExtendedWebElement extendedWebElement)
	{
		click(extendedWebElement, EXPLICIT_TIMEOUT);
	}

	public void click(final ExtendedWebElement extendedWebElement, long timeout)
	{
		extendedWebElement.click(timeout);
	}	

	public void click(String controlInfo, WebElement control)
	{
		click(new ExtendedWebElement(control, controlInfo));
	}
	
	public void clickAny(ExtendedWebElement... elements) {
		clickAny(EXPLICIT_TIMEOUT, elements);
	}

	public void clickAny(long timeout, ExtendedWebElement... elements) {
		// Method which quickly looks for any element and click during timeout
		// sec
		int index = 0;
		boolean clicked = false;
		int counts = 10;
		while (!clicked && index++ < counts) {
			for (int i = 0; i < elements.length; i++) {
				clicked = clickIfPresent(elements[i], timeout / counts);
				if (clicked) {
					break;
				}
			}
		}
		if (!clicked) {
			throw new RuntimeException(
					"Unable to click onto any elements from array: "
							+ elements.toString());
		}
	}
		  
	public boolean clickIfPresent(final ExtendedWebElement extWebElement)
	{
		return clickIfPresent(extWebElement, EXPLICIT_TIMEOUT);
	}	

	public boolean clickIfPresent(final ExtendedWebElement extWebElement, long timeout)
	{
		return extWebElement.clickIfPresent(timeout);
	}	
	
	/**
	 * Double Clicks on element.
	 *
     */
	
	public void doubleClick(final ExtendedWebElement extendedWebElement) {
		extendedWebElement.doubleClick();
	}

	public void doubleClick(String controlInfo, WebElement control)
	{
		doubleClick(new ExtendedWebElement(control, controlInfo));
	}
	
	
	/**
	 * Sends enter to element.
	 * 
	 * @param extendedWebElement
	 *            to send enter.
	 */
	public void pressEnter(final ExtendedWebElement extendedWebElement)
	{
		isElementPresent(extendedWebElement);
		pressEnterSafe(extendedWebElement, true);
		String msg = Messager.ELEMENT_CLICKED.info(extendedWebElement.getName());
		summary.log(msg);
		TestLogCollector.addScreenshotComment(Screenshot.capture(getDriver()), msg);
	}

	/**
	 * Safe enter sending to specified element.
	 * 
	 * @param controlInfo controlInfo
	 * @param control control
	 */
	@Deprecated
	public void pressEnter(String controlInfo, WebElement control)
	{
		pressEnter(new ExtendedWebElement(control, controlInfo));
	}

	@Deprecated
	private void pressEnterSafe(ExtendedWebElement extendedWebElement, boolean startTimer)
	{

		if (startTimer)
		{
			timer = System.currentTimeMillis();
		}
		try
		{
			Thread.sleep(RETRY_TIME);
			if (extendedWebElement.getElement() == null) {
				extendedWebElement = findExtendedWebElement(extendedWebElement.getBy());
			}
			extendedWebElement.getElement().sendKeys(Keys.ENTER);
		}
		catch (UnhandledAlertException e)
		{
			LOGGER.debug(e.getMessage(), e.getCause());
			getDriver().switchTo().alert().accept();
		}
		catch(StaleElementReferenceException e)
		{
			LOGGER.debug(e.getMessage(), e.getCause());
			extendedWebElement = findExtendedWebElement(extendedWebElement.getBy());
		}		
		catch (Exception e)
		{
			LOGGER.debug(e.getMessage(), e.getCause());
			if (System.currentTimeMillis() - timer < EXPLICIT_TIMEOUT * 1000)
			{
				pressEnterSafe(extendedWebElement, false);
			}
			else
			{
				String msg = Messager.ELEMENT_NOT_CLICKED.error(extendedWebElement.getNameWithLocator());
				summary.log(msg);			
				throw new RuntimeException(msg, e); 			
			}
		}
	}
	/**
	 * Check checkbox
	 * 
	 * @param checkbox Element
	 */
	public void check(ExtendedWebElement checkbox)
	{
		checkbox.check();
	}

	/**
	 * Uncheck checkbox
	 * 
	 * @param checkbox Element
	 */
	public void uncheck(ExtendedWebElement checkbox)
	{
		checkbox.uncheck();
	}

	/**
	 * Get checkbox state.
	 * 
	 * @param checkbox
	 *            - checkbox to test
	 * @return - current state
	 */
	public boolean isChecked(final ExtendedWebElement checkbox)
	{
		return checkbox.isChecked();
	}

	
	/**
	 * Inputs file path to specified element.
	 *
     * @param extendedWebElement Element
	 * @param filePath path
	 */
	public void attachFile(final ExtendedWebElement extendedWebElement, String filePath)
	{
		extendedWebElement.attachFile(filePath);
	}

	/**
	 * Opens full or relative URL.
	 * 
	 * @param url
	 *            to open.
	 */
	public void openURL(String url)
	{
		String decryptedURL = cryptoTool.decryptByPattern(url, CRYPTO_PATTERN);
		decryptedURL = decryptedURL.contains("http:") || decryptedURL.contains("https:") ? decryptedURL : Configuration.get(Parameter.URL)
				+ decryptedURL;
		WebDriver drv = getDriver();
		try
		{
			drv.get(decryptedURL);
		}
		catch (UnhandledAlertException e)
		{
			drv.switchTo().alert().accept();
		}
		//AUTO-250 tweak core to start browser in maximized mode - to prevent stability issues
		try
		{			
			drv.manage().window().maximize();				
		}
		catch (Exception e)
		{
			summary.log(e.getMessage());
		    //e.printStackTrace();			
		}		
		
		
		String msg = Messager.OPEN_URL.info(url);
		summary.log(msg);
		TestLogCollector.addScreenshotComment(Screenshot.capture(driver), msg);
	}

	/**
	 * Checks that current URL is as expected.
	 * 
	 * @param expectedURL Expected Url
	 * @return validation result.
	 */
	public boolean isUrlAsExpected(String expectedURL)
	{
		String decryptedURL = cryptoTool.decryptByPattern(expectedURL, CRYPTO_PATTERN);
		decryptedURL = decryptedURL.startsWith("http") ? decryptedURL : Configuration.get(Parameter.URL) + decryptedURL;
		WebDriver drv = getDriver();
		if (LogicUtils.isURLEqual(decryptedURL, drv.getCurrentUrl()))
		{
			summary.log(Messager.EXPECTED_URL.info(drv.getCurrentUrl()));
			return true;
		}
		else
		{
			Messager.UNEXPECTED_URL.error(expectedURL, drv.getCurrentUrl());
			return false;
		}
	}

	/**
	 * Pause for specified timeout.
	 * 
	 * @param timeout
	 *            in seconds.
	 */

	public void pause(long timeout)
	{
		try
		{
			Thread.sleep(timeout * 1000);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}	
	
	public void pause(Double timeout)
	{
		try
		{
			timeout = timeout * 1000;
			long miliSec = timeout.longValue();
			Thread.sleep(miliSec);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}	

	/**
	 * Checks that page title is as expected.
	 * 
	 * @param expectedTitle Expected title
	 * @return validation result.
	 */
	public boolean isTitleAsExpected(final String expectedTitle)
	{
		boolean result;
		final String decryptedExpectedTitle = cryptoTool.decryptByPattern(expectedTitle, CRYPTO_PATTERN);
		final WebDriver drv = getDriver();
		wait = new WebDriverWait(drv, EXPLICIT_TIMEOUT, RETRY_TIME);
		try
		{
			wait.until(new ExpectedCondition<Boolean>()
			{
				public Boolean apply(WebDriver dr)
				{
					return drv.getTitle().contains(decryptedExpectedTitle);
				}
			});
			result = true;
			summary.log(Messager.TITLE_CORERECT.info(drv.getCurrentUrl(), expectedTitle));
		}
		catch (Exception e)
		{
			result = false;
			summary.log(Messager.TITLE_NOT_CORERECT.error(drv.getCurrentUrl(), expectedTitle, drv.getTitle()));
		}
		return result;
	}

	/**
	 * Checks that page suites to expected pattern.
	 * 
	 * @param expectedPattern  Expected Pattern
	 * @return validation result.
	 */
	public boolean isTitleAsExpectedPattern(String expectedPattern)
	{
		boolean result;
		final String decryptedExpectedPattern = cryptoTool.decryptByPattern(expectedPattern, CRYPTO_PATTERN);
		WebDriver drv = getDriver();
		String actual = drv.getTitle();
		Pattern p = Pattern.compile(decryptedExpectedPattern);
		Matcher m = p.matcher(actual);
		if (m.find())
		{
			summary.log(Messager.TITLE_CORERECT.info(drv.getCurrentUrl(), actual));
			result = true;
		}
		else
		{
			summary.log(Messager.TITLE_DOES_NOT_MATCH_TO_PATTERN.error(drv.getCurrentUrl(), expectedPattern, actual));
			result = false;
		}
		return result;
	}

	/**
	 * Go back in browser.
	 */
	public void navigateBack()
	{
		getDriver().navigate().back();
		summary.log(Messager.BACK.info());
	}

	/**
	 * Refresh browser.
	 */
	public void refresh()
	{
		getDriver().navigate().refresh();
		summary.log(Messager.REFRESH.info());
	}

	/**
	 * Refresh browser after timeout.
	 * 
	 * @param timeout
	 *            before refresh.
	 */
	public void refresh(long timeout)
	{
		pause(timeout);
		refresh();
	}

	/**
	 * Selects text in specified select element.
	 *
     * @param extendedWebElement Element
	 * @param selectText select text
	 * @return true if item selected, otherwise false.
	 */
	public boolean select(final ExtendedWebElement extendedWebElement, final String selectText)
	{
		return extendedWebElement.select(selectText);
	}

	/**
	 * Select multiple text values in specified select element.
	 */
	public boolean select(final ExtendedWebElement extendedWebElement, final String[] values)
	{
		return extendedWebElement.select(values);
	}

	public void select(String controlInfo, WebElement control, String selectText)
	{
		select(new ExtendedWebElement(control, controlInfo), selectText);
	}
	/**
	 * Selects value according to text value matcher.
	 *
     * @param extendedWebElement Element
	 * @param matcher {@link} BaseMatcher
	 * @return true if item selected, otherwise false.
	 */
	public boolean selectByMatcher(final ExtendedWebElement extendedWebElement, final BaseMatcher<String> matcher)
	{
		return extendedWebElement.selectByMatcher(matcher);
	}


	/**
	 * Selects item by index in specified select element.
	 * 
	 * @return true if item selected, otherwise false.
	 */
	public boolean select(final ExtendedWebElement extendedWebElement, final int index)
	{
		return extendedWebElement.select(index);		
	}

	public void select(String controlInfo, WebElement control, int index)
	{
		select(new ExtendedWebElement(control, controlInfo), index);
	}
	
	//TODO: review why hover from ExtendedWelement doesn't work

/*	*//**
	 * Hovers over element.
	 *
     *//*
	public void hover(final ExtendedWebElement extendedWebElement) {
		hover(extendedWebElement, null, null);
	}
	public void hover(final ExtendedWebElement extendedWebElement, Integer xOffset, Integer  yOffset)
	{
		extendedWebElement.hover(xOffset, yOffset);
	}

	@Deprecated
	public void hover(String controlInfo, WebElement control)
	{
		hover(new ExtendedWebElement(control, controlInfo));
	}

	*//**
	 * Hovers over element.
	 * 
	 * @param xpathLocator xpathLocator
	 * @param elementName element name
	 *//*
	@Deprecated
	public void hover(String elementName, String xpathLocator)
	{
		WebDriver drv = getDriver();
		Actions action = new Actions(drv);
		action.moveToElement(drv.findElement(By.xpath(xpathLocator))).perform();
		String msg = Messager.HOVER_IMG.info(elementName);
		summary.log(msg);
		TestLogCollector.addScreenshotComment(Screenshot.capture(drv), msg);
	}*/
	
	/**
	 * Hovers over element.
	 *
     */
	public void hover(final ExtendedWebElement extendedWebElement) {
		hover(extendedWebElement, null, null);
	}
	public void hover(final ExtendedWebElement extendedWebElement, Integer xOffset, Integer  yOffset)
	{
		WebDriver drv = getDriver();
		if (isElementPresent(extendedWebElement))
		{
			
			if (!drv.toString().contains("safari")) {
				Actions action = new Actions(drv);
				if (xOffset != null && yOffset != null) {
					action.moveToElement(extendedWebElement.getElement(), xOffset, yOffset);
				}
				else {
					action.moveToElement(extendedWebElement.getElement());
				}

				action.perform();				
			}
			else {
				//https://code.google.com/p/selenium/issues/detail?id=4136
				JavascriptExecutor js = (JavascriptExecutor) drv;
				String locatorType = extendedWebElement.getBy().toString().substring(3);
				String elem = "var elem = document;";
				if (locatorType.startsWith("id")) {
					elem = "var elem = document.getElementById(\""+locatorType.substring(4)+"\");";
				}
				else if (locatorType.startsWith("xpath")) {
					String snippet = "document.getElementByXPath = function(sValue) { var a = this.evaluate(sValue, this, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null); if (a.snapshotLength > 0) { return a.snapshotItem(0); } }; ";
					js.executeScript(snippet);
					elem = "var elem = document.getElementByXPath(\""+locatorType.substring(7)+"\");";
				}
				else if (locatorType.startsWith("className")) {
					elem = "var elem = document.getElementsByClassName(\""+locatorType.substring(14)+"\")[0];";
				}
				String mouseOverScript = elem + " if(document.createEvent){var evObj = document.createEvent('MouseEvents');evObj.initEvent('mouseover', true, false);" +
						" elem.dispatchEvent(evObj);} else if(document.createEventObject) { elem.fireEvent('onmouseover');}";
				js.executeScript(mouseOverScript);
			}

			String msg = Messager.HOVER_IMG.info(extendedWebElement.getName());
			summary.log(msg);
			TestLogCollector.addScreenshotComment(Screenshot.capture(drv), msg);
		}
		else
		{
			summary.log(Messager.ELEMENT_NOT_HOVERED.error(extendedWebElement.getNameWithLocator()));
		}
	}

	public void hover(String controlInfo, WebElement control)
	{
		hover(new ExtendedWebElement(control, controlInfo));
	}

	/**
	 * Hovers over element.
	 * 
	 * @param xpathLocator xpathLocator
	 * @param elementName element name
	 */
	@Deprecated
	public void hover(String elementName, String xpathLocator)
	{
		WebDriver drv = getDriver();
		Actions action = new Actions(drv);
		action.moveToElement(drv.findElement(By.xpath(xpathLocator))).perform();
		String msg = Messager.HOVER_IMG.info(elementName);
		summary.log(msg);
		TestLogCollector.addScreenshotComment(Screenshot.capture(drv), msg);
	}

	public void pressTab()
	{
		Actions builder = new Actions(getDriver());
		builder.sendKeys(Keys.TAB).perform();
	}

	@Deprecated
	public void sendKeys(String keys)
	{
		final String decryptedKeys = cryptoTool.decryptByPattern(keys, CRYPTO_PATTERN);
		Actions builder = new Actions(getDriver());
		builder.sendKeys(decryptedKeys).perform();
	}

	/**
	 * Close alert modal by JS.
	 */
	public void silentAlert()
	{
		WebDriver drv = getDriver();
		if (!(drv instanceof HtmlUnitDriver))
		{
			((JavascriptExecutor) drv).executeScript("window.alert = function(msg) { return true; }");
			((JavascriptExecutor) drv).executeScript("window.confirm = function(msg) { return true; }");
			((JavascriptExecutor) drv).executeScript("window.prompt = function(msg) { return true; }");
		}
	}

	/**
	 * Drags and drops element to specified place.
	 * 
	 * @param from
	 *            - element to drag.
	 * @param to
	 *            - element to drop to.
	 */
	public void dragAndDrop(final ExtendedWebElement from, final ExtendedWebElement to)
	{

		if (isElementPresent(from) && isElementPresent(to))
		{
			WebDriver drv = getDriver();
			if (!drv.toString().contains("safari")) {			
				Actions builder = new Actions(drv);
				Action dragAndDrop = builder.clickAndHold(from.getElement()).moveToElement(to.getElement()).release(to.getElement()).build();
				dragAndDrop.perform();
			} else {			
				WebElement LocatorFrom = from.getElement();
				WebElement LocatorTo = to.getElement();
				String xto=Integer.toString(LocatorTo.getLocation().x);
				String yto=Integer.toString(LocatorTo.getLocation().y);
				((JavascriptExecutor)driver).executeScript("function simulate(f,c,d,e){var b,a=null;for(b in eventMatchers)if(eventMatchers[b].test(c)){a=b;break}if(!a)return!1;document.createEvent?(b=document.createEvent(a),a==\"HTMLEvents\"?b.initEvent(c,!0,!0):b.initMouseEvent(c,!0,!0,document.defaultView,0,d,e,d,e,!1,!1,!1,!1,0,null),f.dispatchEvent(b)):(a=document.createEventObject(),a.detail=0,a.screenX=d,a.screenY=e,a.clientX=d,a.clientY=e,a.ctrlKey=!1,a.altKey=!1,a.shiftKey=!1,a.metaKey=!1,a.button=1,f.fireEvent(\"on\"+c,a));return!0} var eventMatchers={HTMLEvents:/^(?:load|unload|abort|error|select|change|submit|reset|focus|blur|resize|scroll)$/,MouseEvents:/^(?:click|dblclick|mouse(?:down|up|over|move|out))$/}; " +
				"simulate(arguments[0],\"mousedown\",0,0); simulate(arguments[0],\"mousemove\",arguments[1],arguments[2]); simulate(arguments[0],\"mouseup\",arguments[1],arguments[2]); ",
				LocatorFrom,xto,yto);			
			}

			String msg = Messager.ELEMENTS_DRAGGED_AND_DROPPED.info(from.getName(), to.getName());
			summary.log(msg);
			TestLogCollector.addScreenshotComment(Screenshot.capture(drv), msg);
		}
		else
		{
			summary.log(Messager.ELEMENTS_NOT_DRAGGED_AND_DROPPED.error(from.getNameWithLocator(), to.getNameWithLocator()));
		}
	}

	/**
	 * Performs slider move for specified offset.
	 * 
	 * @param slider slider
	 * @param moveX move x
	 * @param moveY move y
	 */
	public void slide(ExtendedWebElement slider, int moveX, int moveY)
	{
		if (isElementPresent(slider))
		{
			WebDriver drv = getDriver();
			(new Actions(drv)).moveToElement(slider.getElement()).dragAndDropBy(slider.getElement(), moveX, moveY).build().perform();
			String msg = Messager.SLIDER_MOVED.info(slider.getNameWithLocator(), String.valueOf(moveX), String.valueOf(moveY));
			summary.log(msg);
			TestLogCollector.addScreenshotComment(Screenshot.capture(drv), msg);
		}
		else
		{
			summary.log(Messager.SLIDER_NOT_MOVED.error(slider.getNameWithLocator(), String.valueOf(moveX), String.valueOf(moveY)));
		}
	}

	/**
	 * Get selected elements from one-value select.
	 * 
	 * @param select Element
	 * @return selected value
	 */
	public String getSelectedValue(ExtendedWebElement select)
	{
		return select.getSelectedValue();
	}

	/**
	 * Get selected elements from multi-value select.
	 * 
	 * @param select Element
	 * @return selected value
	 */
	public List<String> getSelectedValues(ExtendedWebElement select)
	{
		return select.getSelectedValues();
	}

	/**
	 * Accepts alert modal.
	 */
	public void acceptAlert()
	{
		WebDriver drv = getDriver();
		wait = new WebDriverWait(drv, EXPLICIT_TIMEOUT, RETRY_TIME);
		try
		{
			wait.until(new ExpectedCondition<Boolean>()
			{
				public Boolean apply(WebDriver dr)
				{
					return isAlertPresent();
				}
			});
			drv.switchTo().alert().accept();
			Messager.ALERT_ACCEPTED.info("");
		}
		catch (Exception e)
		{
			Messager.ALERT_NOT_ACCEPTED.error("");
		}
	}

	/**
	 * Cancels alert modal.
	 */
	public void cancelAlert()
	{
		WebDriver drv = getDriver();
		wait = new WebDriverWait(drv, EXPLICIT_TIMEOUT, RETRY_TIME);
		try
		{
			wait.until(new ExpectedCondition<Boolean>()
			{
				public Boolean apply(WebDriver dr)
				{
					return isAlertPresent();
				}
			});
			drv.switchTo().alert().dismiss();
			Messager.ALERT_CANCELED.info("");
		}
		catch (Exception e)
		{
			Messager.ALERT_NOT_CANCELED.error("");
		}
	}

	/**
	 * Checks that alert modal is shown.
	 * 
	 * @return whether the alert modal present.
	 */
	public boolean isAlertPresent()
	{
		try
		{
			getDriver().switchTo().alert();
			return true;
		}
		catch (NoAlertPresentException Ex)
		{
			return false;
		}
	}

	// --------------------------------------------------------------------------
	// Methods from v1.0
	// --------------------------------------------------------------------------

	@Deprecated
	public void setElementText(String controlInfo, String frame, String id, String text)
	{
		final String decryptedText = cryptoTool.decryptByPattern(text, CRYPTO_PATTERN);
		WebDriver drv = getDriver();
		((JavascriptExecutor) drv).executeScript(String.format(
				"document.getElementById('%s').contentWindow.document.getElementById('%s').innerHTML='%s'", frame, id, decryptedText));
		String msg = Messager.KEYS_SEND_TO_ELEMENT.info(text, controlInfo);
		summary.log(msg);
		TestLogCollector.addScreenshotComment(Screenshot.capture(drv), msg);
	}

	@Deprecated
	public void setElementText(String controlInfo, String text)
	{
		final String decryptedText = cryptoTool.decryptByPattern(text, CRYPTO_PATTERN);
		WebDriver drv = getDriver();
		((JavascriptExecutor) drv)
				.executeScript(String
						.format("document.contentWindow.getElementsByTagName('ol')[0].getElementsByTagName('li')[1].getElementsByClassName('CodeMirror-lines')[0].getElementsByTagName('div')[0].getElementsByTagName('div')[2].innerHTML=<pre><span class='cm-plsql-word'>'%s'</span></pre>",
								decryptedText));
		String msg = Messager.KEYS_SEND_TO_ELEMENT.info(text, controlInfo);
		summary.log(msg);
		TestLogCollector.addScreenshotComment(Screenshot.capture(drv), msg);
	}

	public boolean isPageOpened(final AbstractPage page)
	{
		return isPageOpened(page, EXPLICIT_TIMEOUT);
	}

	public boolean isPageOpened(final AbstractPage page, long timeout)
	{
		boolean result;
		final WebDriver drv = getDriver();
		wait = new WebDriverWait(drv, timeout, RETRY_TIME);
		try
		{
			wait.until(new ExpectedCondition<Boolean>()
			{
				public Boolean apply(WebDriver dr)
				{
					return LogicUtils.isURLEqual(page.getPageURL(), drv.getCurrentUrl());
				}
			});
			result = true;
		}
		catch (Exception e)
		{
			result = false;
		}
		return result;
	}

	/**
	 * Executes a script on an element
	 * 
	 *  Really should only be used when the web driver is sucking at
	 *       exposing functionality natively
	 * @param script
	 *            The script to execute
	 * @param element
	 *            The target of the script, referenced as arguments[0]
	 */
	public void trigger(String script, WebElement element)
	{
		((JavascriptExecutor) getDriver()).executeScript(script, element);
	}

	/**
	 * Executes a script
	 * 
	 * Really should only be used when the web driver is sucking at
	 *       exposing functionality natively
	 * @param script
	 *            The script to execute
	 */
	public Object trigger(String script)
	{
		return ((JavascriptExecutor) getDriver()).executeScript(script);
	}

	/**
	 * Opens a new tab for the given URL
	 * 
	 * @param url
	 *            The URL to
	 * @throws JavaScriptException
	 *             If unable to open tab
	 */
	public void openTab(String url)
	{
		final String decryptedURL = cryptoTool.decryptByPattern(url, CRYPTO_PATTERN);
		String script = "var d=document,a=d.createElement('a');a.target='_blank';a.href='%s';a.innerHTML='.';d.body.appendChild(a);return a";
		Object element = trigger(String.format(script, decryptedURL));
		if (element instanceof WebElement)
		{
			WebElement anchor = (WebElement) element;
			anchor.click();
			trigger("var a=arguments[0];a.parentNode.removeChild(a);", anchor);
		}
		else
		{
			throw new JavaScriptException(element, "Unable to open tab", 1);
		}
	}

	public void switchWindow() throws NoSuchWindowException
	{
		WebDriver drv = getDriver();
		Set<String> handles = drv.getWindowHandles();
		String current = drv.getWindowHandle();
		if (handles.size() > 1)
		{
			handles.remove(current);
		}
		String newTab = handles.iterator().next();
		drv.switchTo().window(newTab);
	}

	// --------------------------------------------------------------------------
	// Base UI validations
	// --------------------------------------------------------------------------
	public void assertElementPresent(final ExtendedWebElement extWebElement)
	{
		assertElementPresent(extWebElement, EXPLICIT_TIMEOUT);
	}

	public void assertElementPresent(final ExtendedWebElement extWebElement, long timeout)
	{
		extWebElement.assertElementPresent(timeout);
	}

	public void assertElementWithTextPresent(final ExtendedWebElement extWebElement, final String text)
	{
		assertElementWithTextPresent(extWebElement, text, EXPLICIT_TIMEOUT);
	}

	public void assertElementWithTextPresent(final ExtendedWebElement extWebElement, final String text, long timeout)
	{
		extWebElement.assertElementWithTextPresent(text, timeout);
	}

	// --------------------------------------------------------------------------
	// Helpers
	// --------------------------------------------------------------------------

	/**
	 * Find Extended Web Element on page using By.
	 * 
	 * @param by Selenium By locator
	 * @return ExtendedWebElement if exists otherwise null.
	 */
    public ExtendedWebElement findExtendedWebElement(By by) {
    	return findExtendedWebElement(by, by.toString(), EXPLICIT_TIMEOUT);
    }

	/**
	 * Find Extended Web Element on page using By.
	 * 
	 * @param by Selenium By locator
	 * @param timeout
	 * @return ExtendedWebElement if exists otherwise null.
	 */
    public ExtendedWebElement findExtendedWebElement(By by, long timeout) {
    	return findExtendedWebElement(by, by.toString(), timeout);
    }
	
	
	/**
	 * Find Extended Web Element on page using By.
	 * 
	 * @param by Selenium By locator
	 * @param name Element name
	 * @return ExtendedWebElement if exists otherwise null.
	 */
	public ExtendedWebElement findExtendedWebElement(final By by, String name)
	{
		return findExtendedWebElement(by, name, EXPLICIT_TIMEOUT);
	}
	
	/**
	 * Find Extended Web Element on page using By.
	 * 
	 * @param by Selenium By locator
	 * @param name Element name
	 * @param timeout Timeout to find
	 * @return ExtendedWebElement if exists otherwise null.
	 */
	public ExtendedWebElement findExtendedWebElement(final By by, String name, long timeout) {
		ExtendedWebElement element;
		final WebDriver drv = getDriver();
		setImplicitTimeout(0);
		wait = new WebDriverWait(drv, timeout, RETRY_TIME);
		try
		{
			wait.until(new ExpectedCondition<Boolean>()
			{
				public Boolean apply(WebDriver dr)
				{
					return !drv.findElements(by).isEmpty();
				}
			});
			element = new ExtendedWebElement(driver.findElement(by), name, by);
			summary.log(Messager.ELEMENT_FOUND.info(name));
		}
		catch (Exception e)
		{
			element = null;
			summary.log(Messager.ELEMENT_NOT_FOUND.error(name));
			setImplicitTimeout(IMPLICIT_TIMEOUT);
			throw new RuntimeException(e);
		}
		setImplicitTimeout(IMPLICIT_TIMEOUT);
		return element;
	}	
    
	
	
	
	public List<ExtendedWebElement> findExtendedWebElements(By by) {
		return findExtendedWebElements(by, EXPLICIT_TIMEOUT);
	}
	
	public List<ExtendedWebElement> findExtendedWebElements(final By by, long timeout)
	{
		List<ExtendedWebElement> extendedWebElements = new ArrayList<ExtendedWebElement> ();
		List<WebElement> webElements = new ArrayList<WebElement> ();
		
		final WebDriver drv = getDriver();
		drv.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS);
		wait = new WebDriverWait(drv, timeout, RETRY_TIME);
		try
		{
			wait.until(new ExpectedCondition<Boolean>()
			{
				public Boolean apply(WebDriver dr)
				{
					return !drv.findElements(by).isEmpty();
				}
			});
			webElements = driver.findElements(by);
		}
		catch (Exception e)
		{
			//do nothing
		}
		
		for (WebElement element : webElements) {
			String name = "undefined";
			try {
				name = element.getText();
			} catch (Exception e) {/* do nothing*/}

			extendedWebElements.add(new ExtendedWebElement(element, name));
		}		
		drv.manage().timeouts().implicitlyWait(IMPLICIT_TIMEOUT, TimeUnit.SECONDS);
		return extendedWebElements;
	}	
     
	protected void setDriver(WebDriver driver) {
		this.driver = driver;
	}
	
	protected WebDriver getDriver() {
		if (driver == null || driver.toString().contains("null")) {
			driver = DriverPool.getDriverByThread(Thread.currentThread().getId());
		}
		
		return driver;
	}

	public ExtendedWebElement format(ExtendedWebElement element, Object...objects) {
		return format(IMPLICIT_TIMEOUT, element, objects);
	}
	public ExtendedWebElement format(long timeout, ExtendedWebElement element, Object...objects) {
		String locator = element.getBy().toString();
		By by = null;
		if (locator.startsWith("By.id: "))
		{
			by =  By.id(String.format(StringUtils.remove(locator, "By.id: "), objects));
		}
		if (locator.startsWith("By.name: "))
		{
			by =  By.name(String.format(StringUtils.remove(locator, "By.name: "), objects));
		}
		if (locator.startsWith("By.xpath: "))
		{
			by =  By.xpath(String.format(StringUtils.remove(locator, "By.xpath: "), objects));
		}
		if (locator.startsWith("linkText: "))
		{
			by =  By.linkText(String.format(StringUtils.remove(locator, "linkText: "), objects));
		}
		if (locator.startsWith("css: "))
		{
			by =  By.cssSelector(String.format(StringUtils.remove(locator, "css: "), objects));
		}
		if (locator.startsWith("tagName: "))
		{
			by =  By.tagName(String.format(StringUtils.remove(locator, "tagName: "), objects));
		}
		
		ExtendedWebElement res = null;
		try {
			res = findExtendedWebElement(by, by.toString(), timeout); 
		} catch (Exception e) {
			res = new ExtendedWebElement(null, element.getName(), by);
		}
		return res;
	}

}    