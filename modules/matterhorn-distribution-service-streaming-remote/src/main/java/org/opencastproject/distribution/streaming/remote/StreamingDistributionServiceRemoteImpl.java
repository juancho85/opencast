/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.distribution.streaming.remote;

import org.opencastproject.distribution.api.DistributionException;
import org.opencastproject.distribution.api.DistributionService;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobParser;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageParser;
import org.opencastproject.serviceregistry.api.RemoteBase;
import org.opencastproject.util.OsgiUtil;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A remote distribution service invoker.
 */
public class StreamingDistributionServiceRemoteImpl extends RemoteBase implements DistributionService {

  /** The logger */
  private static final Logger logger = LoggerFactory.getLogger(StreamingDistributionServiceRemoteImpl.class);

  /** The distribution channel identifier */
  protected String distributionChannel;

  public StreamingDistributionServiceRemoteImpl() {
    // the service type is not available at construction time. we need to wait for activation to set this value
    super("waiting for activation");
  }

  /** activates the component */
  protected void activate(ComponentContext cc) {
    this.distributionChannel = OsgiUtil.getComponentContextProperty(cc, CONFIG_KEY_STORE_TYPE);
    super.serviceType = JOB_TYPE_PREFIX + this.distributionChannel;
  }

  public String getDistributionType() {
    return this.distributionChannel;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.distribution.api.DistributionService#distribute(String,
   *      org.opencastproject.mediapackage.MediaPackage, String) java.lang.String)
   */
  @Override
  public Job distribute(String channelId, MediaPackage mediaPackage, String elementId) throws DistributionException {
    List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
    params.add(new BasicNameValuePair("mediapackage", MediaPackageParser.getAsXml(mediaPackage)));
    params.add(new BasicNameValuePair("elementId", elementId));
    params.add(new BasicNameValuePair("channelId", channelId));
    HttpPost post = new HttpPost();
    HttpResponse response = null;
    try {
      post.setEntity(new UrlEncodedFormEntity(params));
      response = getResponse(post, HttpStatus.SC_OK, HttpStatus.SC_NO_CONTENT);
      if (response != null) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          logger.info("Distributing {} to {}", elementId, distributionChannel);
          try {
            return JobParser.parseJob(response.getEntity().getContent());
          } catch (Exception e) {
            throw new DistributionException("Unable to distribute mediapackage '" + elementId
                    + "' using a remote distribution service", e);
          }
        } else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT) {
          logger.info("No streaming service available for distribution");
          return null;
        }
      }
    } catch (Exception e) {
      throw new DistributionException("Unable to distribute mediapackage " + elementId
              + " using a remote distribution service proxy.", e);
    } finally {
      closeConnection(response);
    }
    throw new DistributionException("Unable to distribute mediapackage " + elementId
            + " using a remote distribution service proxy.");
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.distribution.api.DistributionService#retract(String,
   *      org.opencastproject.mediapackage.MediaPackage, String)
   */
  @Override
  public Job retract(String channelId, MediaPackage mediaPackage, String elementId) throws DistributionException {
    List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
    params.add(new BasicNameValuePair("mediapackage", MediaPackageParser.getAsXml(mediaPackage)));
    params.add(new BasicNameValuePair("elementId", elementId));
    params.add(new BasicNameValuePair("channelId", channelId));
    HttpPost post = new HttpPost("/retract");
    HttpResponse response = null;
    try {
      post.setEntity(new UrlEncodedFormEntity(params));
      response = getResponse(post, HttpStatus.SC_OK, HttpStatus.SC_NO_CONTENT);
      if (response != null) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          logger.info("Retracting {} from {}", mediaPackage, distributionChannel);
          try {
            return JobParser.parseJob(response.getEntity().getContent());
          } catch (Exception e) {
            throw new DistributionException("Unable to retract mediapackage '" + mediaPackage
                    + "' using a remote distribution service", e);
          }
        } else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT) {
          logger.info("No streaming service available for retracting");
          return null;
        }
      }
    } catch (Exception e) {
      throw new DistributionException("Unable to retract mediapackage " + elementId
              + " using a remote distribution service proxy.", e);
    } finally {
      closeConnection(response);
    }
    throw new DistributionException("Unable to retract mediapackage " + mediaPackage
            + " using a remote distribution service proxy");
  }

}
