/*
 *  This file is part of libautocaptcha
 *  
 *  libautocaptcha is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  libautocaptcha is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with libautocaptcha.  If not, see <http://www.gnu.org/licenses/>.
 *  
 */

package com.googlecode.libautocaptcha;

import java.net.URI;

import com.googlecode.libautocaptcha.Decoder;
import com.googlecode.libautocaptcha.decoder.VodafoneItalyDecoder;

/**
 * Factory to provide decoders.
 * @author Andrea De Pasquale
 */
public class DecoderFactory {
  
  /**
   * Get a decoder for the specified CAPTCHA.
   * @param uri Where the CAPTCHA image was taken from.
   * @return A new decoder, null if none available.
   */
  public static Decoder getDecoderByURI(URI uri) {
    String host = uri.getHost();
    if (host.equals("vodafone.it") || host.endsWith(".vodafone.it"))
      return new VodafoneItalyDecoder();
    
    return null;
  }
  
}
