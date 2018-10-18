package com.blade.mvc.view.template;

import com.blade.Blade;
import com.blade.exception.TemplateException;
import com.blade.kit.IOKit;
import com.blade.mvc.WebContext;
import com.blade.mvc.http.Request;
import com.blade.mvc.http.Session;
import com.blade.mvc.ui.ModelAndView;
import com.blade.mvc.ui.template.DefaultEngine;
import com.blade.mvc.ui.template.TemplateEngine;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.beetl.core.Configuration;
import org.beetl.core.GroupTemplate;
import org.beetl.core.Template;
import org.beetl.core.resource.ClasspathResourceLoader;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BeetlTemplateEngine implements TemplateEngine {

    private static final String        DEFAULT_ENCODING = "UTF-8";
    @Setter
    private              String        suffix           = ".html";
    @Getter
    private              GroupTemplate groupTemplate;

    public BeetlTemplateEngine(Blade blade) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = BeetlTemplateEngine.class.getClassLoader();
        }
        ClasspathResourceLoader resourceLoader = new ClasspathResourceLoader(loader, DefaultEngine.TEMPLATE_PATH, DEFAULT_ENCODING);
        try {
            Configuration cfg = Configuration.defaultConfiguration();
            cfg.setPlaceholderStart(blade.environment().get("beetl.placeholder.start", "${"));
            cfg.setPlaceholderEnd(blade.environment().get("beetl.placeholder.end", "}"));
            cfg.setStatementStart(blade.environment().get("beetl.statement.start", "@"));
            cfg.setStatementEnd(blade.environment().get("beetl.statement.end", ""));
            groupTemplate = new GroupTemplate(resourceLoader, cfg);
            groupTemplate.registerVirtualAttributeClass(Request.class, (obj, attr, ctx) -> {
                Request req = (Request) obj;
                switch (attr) {
                    case "uri":
                        return req.uri();
                    case "url":
                        return req.url();
                    case "remoteAddress":
                        return req.remoteAddress();
                    case "protocol":
                        return req.protocol();
                    case "method":
                        return req.method();
                    case "keepAlive":
                        return req.keepAlive();
                    case "queryString":
                        return req.queryString();
                    default:
                        return null;
                }
            });
            groupTemplate.registerVirtualAttributeClass(Session.class, (obj, attr, ctx) -> {
                Session session = (Session) obj;
                switch (attr) {
                    case "id":
                        return session.id();
                    case "ip":
                        return session.ip();
                    case "created":
                        return new Date(session.created());
                    case "expired":
                        return new Date(session.expired());
                    default:
                        return null;
                }
            });


        } catch (IOException e) {
            throw new TemplateException(e);
        }
    }

    public BeetlTemplateEngine(GroupTemplate groupTemplate) {
        this.groupTemplate = groupTemplate;
    }

    @Override
    public void render(ModelAndView modelAndView, Writer writer) throws TemplateException {
        String view = modelAndView.getView();
        Template t;
        if (view.startsWith("redirect:")) {
            WebContext.response().redirect(view.substring(9));
            return;
        }
        if (!view.contains("#")) {
            t = groupTemplate.getTemplate(view + suffix);
        } else {
            String[] split = view.split("#");
            if (split.length == 2) {
                t = groupTemplate.getAjaxTemplate(split[0] + suffix, split[1]);
            } else {
                throw new TemplateException("视图名称有误：" + view);
            }
        }
        Request request = WebContext.request();
        Map<String, Object> attributes = new HashMap<>();
        attributes.putAll(modelAndView.getModel());
        attributes.putAll(request.attributes());
        Session session = request.session();
        if (null != session) {
            attributes.put("session", session.attributes());
        }
        attributes.put("request", request);
        t.fastBinding(attributes);
        t.renderTo(writer);
        IOKit.closeQuietly(writer);
    }
}
