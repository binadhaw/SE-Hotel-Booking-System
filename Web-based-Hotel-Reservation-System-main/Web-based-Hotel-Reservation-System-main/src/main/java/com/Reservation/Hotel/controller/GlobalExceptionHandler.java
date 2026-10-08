package com.Reservation.Hotel.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Turns common user mistakes into a friendly message instead of an error page. */
@ControllerAdvice
public class GlobalExceptionHandler {

    /** A photo or receipt above the upload limit: go back to the form and explain. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "That file is too large. Each photo or receipt must be 5 MB or smaller.");
        String referer = request.getHeader("Referer");
        // Only return to a page on this site
        if (referer != null) {
            String host = request.getScheme() + "://" + request.getServerName();
            if (referer.startsWith(host)) {
                int pathStart = referer.indexOf('/', host.length());
                if (pathStart > 0) return "redirect:" + referer.substring(pathStart);
            }
        }
        return "redirect:/dashboard";
    }
}
