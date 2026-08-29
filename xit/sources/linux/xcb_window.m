/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.0
 */

#include <xide/xit.h>

#include "window_backend.h"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GL/gl.h>
#include <xcb/xcb.h>

#include <stdlib.h>
#include <string.h>

typedef struct XitXcbVisual
{
  xcb_visualid_t id;
  uint8_t depth;
  bool found;
} XitXcbVisual;

static xcb_screen_t *xit_xcb_screen(xcb_connection_t *connection, int screen_index)
{
  const xcb_setup_t *setup = xcb_get_setup(connection);
  xcb_screen_iterator_t iterator = xcb_setup_roots_iterator(setup);

  while(screen_index > 0 && iterator.rem != 0)
  {
    xcb_screen_next(&iterator);
    --screen_index;
  }

  return iterator.rem == 0 ? nullptr : iterator.data;
}

static XitXcbVisual xit_xcb_visual(xcb_screen_t *screen, xcb_visualid_t visual_id)
{
  xcb_depth_iterator_t depth_iterator = xcb_screen_allowed_depths_iterator(screen);

  while(depth_iterator.rem != 0)
  {
    xcb_visualtype_iterator_t visual_iterator = xcb_depth_visuals_iterator(depth_iterator.data);
    while(visual_iterator.rem != 0)
    {
      if(visual_iterator.data->visual_id == visual_id)
      {
        return (XitXcbVisual){
            .id = visual_id,
            .depth = depth_iterator.data->depth,
            .found = true,
        };
      }
      xcb_visualtype_next(&visual_iterator);
    }
    xcb_depth_next(&depth_iterator);
  }

  return (XitXcbVisual){0};
}

static xcb_atom_t xit_xcb_atom(xcb_connection_t *connection, const char *name)
{
  const uint16_t length = (uint16_t)strlen(name);
  xcb_intern_atom_cookie_t cookie = xcb_intern_atom(connection, false, length, name);
  xcb_intern_atom_reply_t *reply = xcb_intern_atom_reply(connection, cookie, nullptr);
  if(reply == nullptr)
  {
    return XCB_ATOM_NONE;
  }

  const xcb_atom_t atom = reply->atom;
  free(reply);
  return atom;
}

static bool xit_xcb_configure_protocols(xcb_connection_t *connection,
                                        xcb_window_t window,
                                        const char *title,
                                        xcb_atom_t *delete_window)
{
  const xcb_atom_t wm_protocols = xit_xcb_atom(connection, "WM_PROTOCOLS");
  *delete_window = xit_xcb_atom(connection, "WM_DELETE_WINDOW");
  if(wm_protocols == XCB_ATOM_NONE || *delete_window == XCB_ATOM_NONE)
  {
    return false;
  }

  xcb_change_property(connection,
                      XCB_PROP_MODE_REPLACE,
                      window,
                      XCB_ATOM_WM_NAME,
                      XCB_ATOM_STRING,
                      8,
                      (uint32_t)strlen(title),
                      title);
  xcb_change_property(
      connection, XCB_PROP_MODE_REPLACE, window, wm_protocols, XCB_ATOM_ATOM, 32, 1, delete_window);
  return true;
}

static EGLConfig xit_egl_config(EGLDisplay display)
{
  const EGLint attributes[] = {
      EGL_SURFACE_TYPE,
      EGL_WINDOW_BIT,
      EGL_RENDERABLE_TYPE,
      EGL_OPENGL_BIT,
      EGL_RED_SIZE,
      8,
      EGL_GREEN_SIZE,
      8,
      EGL_BLUE_SIZE,
      8,
      EGL_ALPHA_SIZE,
      8,
      EGL_NONE,
  };
  EGLConfig config = nullptr;
  EGLint count = 0;
  return eglChooseConfig(display, attributes, &config, 1, &count) == EGL_TRUE && count == 1
             ? config
             : nullptr;
}

static EGLContext
xit_egl_context_version(EGLDisplay display, EGLConfig config, EGLint major, EGLint minor)
{
  const EGLint attributes[] = {
      EGL_CONTEXT_MAJOR_VERSION,
      major,
      EGL_CONTEXT_MINOR_VERSION,
      minor,
      EGL_CONTEXT_OPENGL_PROFILE_MASK_KHR,
      EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT_KHR,
      EGL_NONE,
  };
  return eglCreateContext(display, config, EGL_NO_CONTEXT, attributes);
}

static EGLContext xit_egl_context(EGLDisplay display, EGLConfig config)
{
  EGLContext context = xit_egl_context_version(display, config, 4, 5);
  if(context == EGL_NO_CONTEXT)
  {
    context = xit_egl_context_version(display, config, 4, 2);
  }
  return context;
}

XitStatus xit_linux_x11_window_run(const XitWindowConfig *config)
{
  XitStatus status = XIT_STATUS_PLATFORM_UNAVAILABLE;
  xcb_connection_t *connection = nullptr;
  xcb_window_t window = XCB_WINDOW_NONE;
  xcb_colormap_t colormap = XCB_COLORMAP_NONE;
  EGLDisplay egl_display = EGL_NO_DISPLAY;
  EGLSurface egl_surface = EGL_NO_SURFACE;
  EGLContext egl_context = EGL_NO_CONTEXT;

  int screen_index = 0;
  connection = xcb_connect(nullptr, &screen_index);
  if(connection == nullptr || xcb_connection_has_error(connection) != 0)
  {
    goto cleanup;
  }

  xcb_screen_t *screen = xit_xcb_screen(connection, screen_index);
  if(screen == nullptr)
  {
    goto cleanup;
  }

  egl_display = eglGetPlatformDisplay(EGL_PLATFORM_XCB_EXT, connection, nullptr);
  if(egl_display == EGL_NO_DISPLAY || eglInitialize(egl_display, nullptr, nullptr) != EGL_TRUE)
  {
    goto cleanup;
  }

  if(eglBindAPI(EGL_OPENGL_API) != EGL_TRUE)
  {
    status = XIT_STATUS_GRAPHICS_UNAVAILABLE;
    goto cleanup;
  }

  EGLConfig egl_config = xit_egl_config(egl_display);
  EGLint native_visual_id = 0;
  if(egl_config == nullptr ||
     eglGetConfigAttrib(egl_display, egl_config, EGL_NATIVE_VISUAL_ID, &native_visual_id) !=
         EGL_TRUE)
  {
    status = XIT_STATUS_GRAPHICS_UNAVAILABLE;
    goto cleanup;
  }

  const XitXcbVisual visual = xit_xcb_visual(screen, (xcb_visualid_t)native_visual_id);
  if(!visual.found)
  {
    status = XIT_STATUS_GRAPHICS_UNAVAILABLE;
    goto cleanup;
  }

  colormap = xcb_generate_id(connection);
  xcb_create_colormap(connection, XCB_COLORMAP_ALLOC_NONE, colormap, screen->root, visual.id);

  const uint32_t event_mask =
      XCB_EVENT_MASK_EXPOSURE | XCB_EVENT_MASK_STRUCTURE_NOTIFY | XCB_EVENT_MASK_KEY_PRESS;
  const uint32_t values[] = {
      screen->black_pixel,
      event_mask,
      colormap,
  };
  window = xcb_generate_id(connection);
  const uint32_t value_mask = XCB_CW_BACK_PIXEL | XCB_CW_EVENT_MASK | XCB_CW_COLORMAP;
  xcb_void_cookie_t create_cookie = xcb_create_window_checked(connection,
                                                              visual.depth,
                                                              window,
                                                              screen->root,
                                                              0,
                                                              0,
                                                              (uint16_t)config->width,
                                                              (uint16_t)config->height,
                                                              0,
                                                              XCB_WINDOW_CLASS_INPUT_OUTPUT,
                                                              visual.id,
                                                              value_mask,
                                                              values);
  xcb_generic_error_t *create_error = xcb_request_check(connection, create_cookie);
  if(create_error != nullptr)
  {
    free(create_error);
    goto cleanup;
  }

  xcb_atom_t delete_window = XCB_ATOM_NONE;
  const char *title = config->title == nullptr ? "XIT black surface" : config->title;
  if(!xit_xcb_configure_protocols(connection, window, title, &delete_window))
  {
    goto cleanup;
  }

  const EGLAttrib surface_attributes[] = {EGL_NONE};
  egl_surface =
      eglCreatePlatformWindowSurface(egl_display, egl_config, &window, surface_attributes);
  egl_context = xit_egl_context(egl_display, egl_config);
  if(egl_surface == EGL_NO_SURFACE || egl_context == EGL_NO_CONTEXT ||
     eglMakeCurrent(egl_display, egl_surface, egl_surface, egl_context) != EGL_TRUE)
  {
    status = XIT_STATUS_GRAPHICS_UNAVAILABLE;
    goto cleanup;
  }

  GLint context_major = 0;
  GLint context_minor = 0;
  glGetIntegerv(GL_MAJOR_VERSION, &context_major);
  glGetIntegerv(GL_MINOR_VERSION, &context_minor);
  if(context_major < 4 || (context_major == 4 && context_minor < 2))
  {
    status = XIT_STATUS_GRAPHICS_UNAVAILABLE;
    goto cleanup;
  }

  xcb_map_window(connection, window);
  xcb_flush(connection);
  eglSwapInterval(egl_display, 1);

  glViewport(0, 0, (GLsizei)config->width, (GLsizei)config->height);
  glClearColor(0.0F, 0.0F, 0.0F, 1.0F);

  bool running = true;
  uint32_t frame = 0;
  while(running)
  {
    xcb_generic_event_t *event = nullptr;
    while((event = xcb_poll_for_event(connection)) != nullptr)
    {
      const uint8_t event_type = event->response_type & 0x7fU;
      if(event_type == XCB_CLIENT_MESSAGE)
      {
        const xcb_client_message_event_t *client = (const xcb_client_message_event_t *)event;
        running = client->data.data32[0] != delete_window;
      }
      else if(event_type == XCB_DESTROY_NOTIFY)
      {
        running = false;
      }
      else if(event_type == XCB_CONFIGURE_NOTIFY)
      {
        const xcb_configure_notify_event_t *configure = (const xcb_configure_notify_event_t *)event;
        glViewport(0, 0, configure->width, configure->height);
      }
      free(event);
    }

    glClear(GL_COLOR_BUFFER_BIT);
    if(eglSwapBuffers(egl_display, egl_surface) != EGL_TRUE)
    {
      status = XIT_STATUS_GRAPHICS_UNAVAILABLE;
      goto cleanup;
    }

    ++frame;
    if(config->frame_limit != 0 && frame >= config->frame_limit)
    {
      running = false;
    }
  }

  status = XIT_STATUS_OK;

cleanup:
  if(egl_display != EGL_NO_DISPLAY)
  {
    eglMakeCurrent(egl_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if(egl_context != EGL_NO_CONTEXT)
    {
      eglDestroyContext(egl_display, egl_context);
    }
    if(egl_surface != EGL_NO_SURFACE)
    {
      eglDestroySurface(egl_display, egl_surface);
    }
    eglTerminate(egl_display);
  }
  if(connection != nullptr)
  {
    if(window != XCB_WINDOW_NONE)
    {
      xcb_destroy_window(connection, window);
    }
    if(colormap != XCB_COLORMAP_NONE)
    {
      xcb_free_colormap(connection, colormap);
    }
    xcb_disconnect(connection);
  }
  return status;
}
