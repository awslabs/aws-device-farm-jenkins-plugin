let vpceSelect = document.querySelector("[name='ifVpce']");
const vpcTextbox = document.querySelector(".vpcId");
const projectSelect = document.querySelector(".projectSelect");

// disable vpce select option if project already has VPC Settings
if (vpcTextbox && vpcTextbox.value) {
    vpceSelect.disabled = true;
}

// check again for VPC status every time a new project is selected
const handleClick = () => {
    if (vpcTextbox && vpcTextbox.value) {
        vpceSelect.disabled = true;
    } else {
        vpceSelect.disabled = false;
    }
}

projectSelect.addEventListener("click", handleClick);


