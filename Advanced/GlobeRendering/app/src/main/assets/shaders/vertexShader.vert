#version 300 es

uniform mat4 u_mvpMatrix;
layout(location = 0) in vec4 a_position;
layout(location = 1) in vec3 a_normal;
out vec3 v_normal;
void main()
{
  v_normal = a_normal;
  gl_Position = u_mvpMatrix * a_position;
}