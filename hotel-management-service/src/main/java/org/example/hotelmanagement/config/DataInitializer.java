package org.example.hotelmanagement.config;

import org.example.hotelmanagement.entity.Hotel;
import org.example.hotelmanagement.entity.Room;
import org.example.hotelmanagement.repository.HotelRepository;
import org.example.hotelmanagement.repository.RoomRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initData(HotelRepository hotelRepository, RoomRepository roomRepository) {
        return args -> {
            if (hotelRepository.count() == 0) {
                Hotel hotel1 = hotelRepository.save(Hotel.builder()
                        .name("Grand Hotel Moscow")
                        .address("Red Square, Moscow")
                        .build());

                Hotel hotel2 = hotelRepository.save(Hotel.builder()
                        .name("Seaside Resort")
                        .address("Nevsky Prospect, St. Petersburg")
                        .build());

                roomRepository.save(Room.builder()
                        .hotelId(hotel1.getId())
                        .roomNumber("101")
                        .available(true)
                        .timesBooked(0)
                        .build());

                roomRepository.save(Room.builder()
                        .hotelId(hotel1.getId())
                        .roomNumber("102")
                        .available(true)
                        .timesBooked(0)
                        .build());

                roomRepository.save(Room.builder()
                        .hotelId(hotel1.getId())
                        .roomNumber("201")
                        .available(true)
                        .timesBooked(0)
                        .build());

                roomRepository.save(Room.builder()
                        .hotelId(hotel2.getId())
                        .roomNumber("301")
                        .available(true)
                        .timesBooked(0)
                        .build());

                roomRepository.save(Room.builder()
                        .hotelId(hotel2.getId())
                        .roomNumber("302")
                        .available(true)
                        .timesBooked(0)
                        .build());
            }
        };
    }
}
