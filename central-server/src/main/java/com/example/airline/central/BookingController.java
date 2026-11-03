package com.example.airline.central;

import com.example.airline.model.BookingRequest;
import com.example.airline.model.Receipt;
import com.example.airline.model.RegisterBookingRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public Receipt book(@RequestBody BookingRequest request) {
        return bookingService.book(request);
    }

    @GetMapping("/{bookingReference}")
    public Receipt get(@PathVariable String bookingReference) {
        return bookingService.get(bookingReference);
    }

    @PostMapping("/{bookingReference}/cancel")
    public Receipt cancel(@PathVariable String bookingReference) {
        return bookingService.cancel(bookingReference);
    }

    @PostMapping("/register")
    public Receipt register(@RequestBody RegisterBookingRequest request) {
        return bookingService.registerFromThickClient(request);
    }

    @PostMapping("/{bookingReference}/mark-cancelled")
    public Receipt markCancelled(@PathVariable String bookingReference) {
        return bookingService.markCancelledByThickClient(bookingReference);
    }
}
