/* SVGTrainTracksText.java created by rmendis on Thu 08-Feb-2001 */
package org.svgobjects.examples.hello;

import com.webobjects.foundation.*;
import com.webobjects.appserver.*;

public class SVGTrainTracksText extends WOComponent {

    public SVGTrainTracksText(WOContext context) {
        super(context);
    }

    /*
     * non synching component
     */
    public boolean synchronizesVariablesWithBindings() {
        return false;
    }
}