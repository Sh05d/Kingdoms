package com.kingdom.DTO.OUT;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
// this help to take book info from Google api to send it to AI
public class GoogleBookDTO {
    private String googleBookId;
    private String title;
    private List<String> authors;
    private String description;
    private Integer pageCount;
}
