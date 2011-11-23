/**
 *
 * This file is part of Jahia: An integrated WCM, DMS and Portal Solution
 * Copyright (C) 2002-2009 Jahia Limited. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * As a special exception to the terms and conditions of version 2.0 of
 * the GPL (or any later version), you may redistribute this Program in connection
 * with Free/Libre and Open Source Software ("FLOSS") applications as described
 * in Jahia's FLOSS exception. You should have recieved a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license"
 *
 * Commercial and Supported Versions of the program
 * Alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms contained in a separate written agreement
 * between you and Jahia Limited. If you are unsure which license is appropriate
 * for your use, please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.watermark;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.drools.spi.KnowledgeHelper;
import org.im4java.core.CompositeCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.jahia.api.Constants;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.rules.AddedNodeFact;
import org.jahia.services.content.rules.ChangedPropertyFact;
import org.jahia.services.image.ImageMagickImage;
import org.jahia.services.image.ImageMagickImageService;
import org.jahia.services.image.JahiaImageService;
import org.springframework.beans.factory.InitializingBean;

import javax.jcr.RepositoryException;
import java.io.*;

/**
 * This class use IM4J API ti call ImageMagick tools to compose an image by blending them together.
 *
 * @author : Cédric mailleux
 * @since : JAHIA 6.6
 * Created : 11/23/11
 */
public class WatermarkRuleService implements InitializingBean {
    private transient static Logger logger = Logger.getLogger(WatermarkRuleService.class);
    private JahiaImageService imageService;
    private JCRTemplate jcrTemplate;
    private boolean activated;

    /**
     * This method blend two image together.
     * @param nodeFact The source image
     * @param imageName The watermark image
     * @param blendValue The blending value (0-100) define the opacity of the watermark image compared to the original image.
     * @param gravityValue Where to place the watermark value are (NorthWest,North,NorthEast,East,SouthEast,South,SouthWest,West,Center)
     * @param drools current set of facts to be handled by drools.
     */
    public void watermark(final AddedNodeFact nodeFact, final String imageName, int blendValue, String gravityValue,
                          final KnowledgeHelper drools) {
        if (activated) {
            final IMOperation operation = new IMOperation();
            operation.blend(blendValue);
            operation.gravity(gravityValue);
            operation.addImage();
            operation.addImage();
            operation.addImage();
            try {
                jcrTemplate.doExecuteWithSystemSession(new JCRCallback<Object>() {
                    public Object doInJCR(JCRSessionWrapper session) throws RepositoryException {
                        try {
                            JCRNodeWrapper watermarkedNode = nodeFact.getNode();
                            JCRNodeWrapper node = session.getNode(
                                    watermarkedNode.getResolveSite().getPath() + "/files/" + imageName);
                            ImageMagickImage watermark = (ImageMagickImage) imageService.getImage(node);
                            ImageMagickImage destImage = (ImageMagickImage) imageService.getImage(watermarkedNode);
                            File resultFile = File.createTempFile("watermark", null);
                            CompositeCmd cmd = new CompositeCmd();
                            cmd.run(operation, watermark.getFile().getAbsolutePath(),
                                    destImage.getFile().getAbsolutePath(), resultFile.getAbsolutePath());
                            InputStream fis = new BufferedInputStream(new FileInputStream(resultFile));
                            try {
                                watermarkedNode.getParent().uploadFile(watermarkedNode.getName(), fis,
                                        watermarkedNode.getFileContent().getContentType());
                                session.save();
                            } finally {
                                IOUtils.closeQuietly(fis);
                            }
                            AddedNodeFact watermarkedNodeFact = new AddedNodeFact(watermarkedNode);
                            drools.insert(new ChangedPropertyFact(watermarkedNodeFact, Constants.JCR_DATA, resultFile,
                                    drools));
                        } catch (IOException e) {
                            logger.error(e.getMessage(), e);
                        } catch (InterruptedException e) {
                            logger.error(e.getMessage(), e);
                        } catch (IM4JavaException e) {
                            logger.error(e.getMessage(), e);
                        }
                        return null;
                    }
                });
            } catch (RepositoryException e) {
                logger.warn("Issue while watermarking an image : " + e.getMessage(), e);
            }
        }
    }

    public void setImageService(JahiaImageService imageService) {
        this.imageService = imageService;
    }

    public void setJcrTemplate(JCRTemplate jcrTemplate) {
        this.jcrTemplate = jcrTemplate;
    }

    /**
     * Invoked by a BeanFactory after it has set all bean properties supplied
     * (and satisfied BeanFactoryAware and ApplicationContextAware).
     * <p>This method allows the bean instance to perform initialization only
     * possible when all bean properties have been set and to throw an
     * exception in the event of misconfiguration.
     *
     * @throws Exception in the event of misconfiguration (such
     *                   as failure to set an essential property) or if initialization fails.
     */
    public void afterPropertiesSet() throws Exception {
        activated = imageService instanceof ImageMagickImageService;
    }
}
