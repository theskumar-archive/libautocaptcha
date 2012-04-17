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

import net.sourceforge.javaocr.Image;

/**
 * Interface for every decoder class. 
 * @author Andrea De Pasquale
 */
public interface Decoder {
  
  /**
   * Decode an image, getting back the text solution.
   * @param captcha Image containing a visual CAPTCHA.
   * @return Text representation of the CAPTCHA.
   */
  String decode(Image captcha);
  
}
