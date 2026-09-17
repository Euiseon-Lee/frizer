package com.euiseon.friger.inventory.bulk;

import com.euiseon.friger.inventory.dao.FoodMasterDao;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/inventory/bulk")
public class BulkRegistrationController {
    private final BulkRegistrationService service;
    private final BulkWorkbook workbook;
    private final FoodMasterDao masters;
    public BulkRegistrationController(BulkRegistrationService service,BulkWorkbook workbook,FoodMasterDao masters) { this.service=service;this.workbook=workbook;this.masters=masters; }
    @GetMapping
    String page(Model model,HttpSession session) { owner(session); return "inventory/bulk"; }
    @GetMapping("/template")
    ResponseEntity<byte[]> template() throws IOException {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=frizer-bulk-template.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(workbook.template(masters.registrationChoices()));
    }
    @PostMapping("/preview")
    String preview(org.springframework.web.multipart.MultipartHttpServletRequest request,@RequestParam UUID formToken,HttpSession session,Model model) throws IOException {
        if(!owner(session).equals(formToken)) { model.addAttribute("error","파일을 다시 선택해줘.");return page(model,session); }
        var files=request.getMultiFileMap().values().stream().flatMap(java.util.Collection::stream).toList();
        if(files.size()!=1 || !"file".equals(files.getFirst().getName()) || files.getFirst().isEmpty()) {
            model.addAttribute("error","엑셀 파일은 1개만 선택해줘.");return page(model,session);
        }
        var file=files.getFirst();
        if(file.getOriginalFilename()==null || !file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx") || file.getSize()>BulkWorkbook.MAX_BYTES) {
            model.addAttribute("error","2MB 이하의 .xlsx 파일을 선택해줘.");return page(model,session);
        }
        try { model.addAttribute("preview",service.preview(file.getBytes(),owner(session))); model.addAttribute("fileName",file.getOriginalFilename()); }
        catch(IllegalArgumentException e) { model.addAttribute("error",e.getMessage()); }
        return page(model,session);
    }
    @PostMapping("/commit")
    String commit(@RequestParam UUID requestId,@RequestParam(defaultValue="false") boolean repeat,HttpSession session,RedirectAttributes redirect) {
        try { int count=service.commit(requestId,owner(session),repeat); redirect.addFlashAttribute("successMessage",count+"개 항목을 등록했어!");return "redirect:/inventory"; }
        catch(IllegalArgumentException e) { redirect.addFlashAttribute("error",e.getMessage());return "redirect:/inventory/bulk"; }
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    String tooLarge(RedirectAttributes redirect) { redirect.addFlashAttribute("error","2MB 이하의 .xlsx 파일을 선택해줘."); return "redirect:/inventory/bulk"; }
    public static UUID owner(HttpSession session) {
        synchronized(session) {
            UUID owner=(UUID)session.getAttribute("bulkOwner");
            if(owner==null) { owner=UUID.randomUUID();session.setAttribute("bulkOwner",owner); }
            return owner;
        }
    }
}
